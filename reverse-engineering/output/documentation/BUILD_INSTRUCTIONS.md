# Reconstructed Project Build Instructions

## Prerequisites
- Android Studio Jellyfish / Koala / Ladybug or Gradle 8.x
- JDK 17 or higher (`JAVA_HOME` pointing to JDK 17/21/25)
- Android SDK with API Level 30+ installed

## Building via Command Line
```bash
cd reverse-engineering/output/reconstructed-project
./gradlew assembleDebug
```

Output APK will be generated at:
`reverse-engineering/output/reconstructed-project/app/build/outputs/apk/debug/app-debug.apk`
and copied to:
`reverse-engineering/output/builds/reconstructed-debug.apk`
