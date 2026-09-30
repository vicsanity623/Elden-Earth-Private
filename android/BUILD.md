# Elden Earth — Android Build Guide

## Prerequisites

1. **Android Studio** (Iguana or later) — https://developer.android.com/studio
2. **Java JDK 17** — bundled with Android Studio
3. **Google Services JSON** — download from Firebase Console → Project Settings → Android app
4. **Google Play Developer Account** — $25 one-time fee at https://play.google.com/console

## Setup Steps

### 1. Install Android Studio
Download and install Android Studio. During setup, install:
- Android SDK 34
- Android SDK Build-Tools 34
- Android Emulator (for testing)

### 2. Place Google Services Config
Download `google-services.json` from Firebase Console:
- Go to Firebase Console → Project Settings → Your Android App → Download `google-services.json`
- Place it at: `android/app/google-services.json`

### 3. Generate a Signing Key
For release builds, create a keystore:
```bash
keytool -genkey -v -keystore elden-earth-release.keystore \
  -alias eldenearth -keyalg RSA -keysize 2048 -validity 10000
```
Note the password you set. Then in `android/app/build.gradle`, add:
```groovy
android {
    signingConfigs {
        release {
            storeFile file('../elden-earth-release.keystore')
            storePassword 'YOUR_PASSWORD'
            keyAlias 'eldenearth'
            keyPassword 'YOUR_PASSWORD'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

### 4. Open in Android Studio
```
File → Open → select the `android/` folder
```
Android Studio will sync Gradle automatically. Wait for sync to complete.

### 5. Add Web Assets
Copy your built web files into the Android assets folder:
```bash
# From the EE root directory
cp -r js/ android/app/src/main/assets/js/
cp -r css/ android/app/src/main/assets/css/
cp -r assets/ android/app/src/main/assets/assets/
cp index.html android/app/src/main/assets/
cp config.js android/app/src/main/assets/
```

Or configure the app to load from URL (current default):
- Edit `GAME_URL` in `MainActivity.java` to point to your hosted web app

### 6. Include native-bridge.js
Add `js/native-bridge.js` to your `index.html`:
```html
<script src="js/native-bridge.js"></script>
```

### 7. Add Firebase Version Config Document
In Firestore, create a document at `app_config/latest_version`:
```json
{
  "versionCode": 1,
  "versionName": "0.1.11.01b",
  "downloadUrl": "https://your-host.com/elden-earth-v0.1.11.01b.apk",
  "required": false,
  "changelog": "Initial beta release",
  "releasedAt": "2026-12-01T00:00:00Z"
}
```

## Building

### Debug Build (for testing)
```bash
cd android
./gradlew assembleDebug
```
APK output: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release Build (for Play Store)
```bash
cd android
./gradlew assembleRelease
```
APK output: `android/app/build/outputs/apk/release/app-release.apk`

### Build via Android Studio
1. Open `android/` in Android Studio
2. Select build variant: `debug` or `release`
3. Build → Build Bundle(s) / APK(s) → Build APK(s)

## Self-Update Flow

1. App boots → `BootActivity` shows native loading sequence
2. After boot, `UpdateManager` checks `app_config/latest_version` in Firestore
3. If `versionCode` > current app version → shows "Update available" in boot log
4. On game load, `NativeBridge.isUpdateAvailable()` returns true
5. Player can tap to update → APK downloads → prompts to install

## To Push an Update

1. Increment `versionCode` and `versionName` in `android/app/build.gradle`
2. Build new APK
3. Upload APK to hosting (Firebase Storage, your server, etc.)
4. Update `app_config/latest_version` document in Firestore:
   - `versionCode`: new code
   - `versionName`: new version string
   - `downloadUrl`: direct download link to new APK
5. Next boot, all users see the update prompt

## Play Store Submission

### Required Assets
- **App icon**: 512x512 PNG (high-res)
- **Feature graphic**: 1024x500 PNG
- **Screenshots**: minimum 2, phone + tablet recommended
- **Privacy policy URL**: required (you collect location data)
- **App description**: describing the game

### Data Safety Declaration (Google Play Console)
Declare these data collections:
- **Location** — for territory claiming
- **Device ID** — for anti-cheat
- **Web browsing** — WebView loads web content

### Content Rating
Complete the IARC content rating questionnaire. Elden Earth should rate as:
- Violence: None
- Blood: None
- Fear: None
- Mature themes: None

### Review Checklist
- [ ] App launches without crashes
- [ ] Boot sequence plays correctly
- [ ] Game loads and plays in WebView
- [ ] Location permission request works
- [ ] Back button handling works
- [ ] No black screens or freezes
- [ ] Privacy policy URL is valid
- [ ] Data safety form is accurate
