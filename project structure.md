android-rat/
├── server/           # Node.js backend + dashboard
│   ├── package.json
│   ├── server.js
│   ├── public/
│   │   ├── index.html   # Full dashboard SPA
│   │   ├── style.css
│   │   └── app.js
│   └── builder/     # APK builder
│       ├── build-apk.js
│       └── templates/
│           └── ConfigTemplate.kt
├── android-app/      # Android Studio project
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── app/
│   │   ├── build.gradle.kts
│   │   ├── proguard-rules.pro
│   │   └── src/
│   │       └── main/
│   │           ├── AndroidManifest.xml
│   │           ├── res/
│   │           │   ├── xml/
│   │           │   │   ├── accessibility_service_config.xml
│   │           │   │   └── network_security_config.xml
│   │           │   ├── drawable/
│   │           │   │   └── ic_notification.xml
│   │           │   └── values/
│   │           │       └── strings.xml
│   │           └── java/com/hackerai/rat/
│   │               ├── RATApplication.kt
│   │               ├── MainActivity.kt
│   │               ├── Config.kt
│   │               ├── utils/
│   │               │   ├── DeviceUtils.kt
│   │               │   ├── CryptoUtils.kt
│   │               │   └── PermissionUtils.kt
│   │               ├── services/
│   │               │   ├── SocketService.kt
│   │               │   ├── RATAccessibilityService.kt
│   │               │   ├── ScreenCaptureService.kt
│   │               │   └── BootReceiver.kt
│   │               ├── managers/
│   │               │   ├── DeviceManager.kt
│   │               │   ├── LocationManager.kt
│   │               │   ├── MessagingManager.kt
│   │               │   ├── MediaManager.kt
│   │               │   ├── FileManager.kt
│   │               │   ├── ActionManager.kt
│   │               │   └── TelegramManager.kt
│   │               └── models/
│   │                   └── DataModels.kt
