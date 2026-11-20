# Original vs. Reconstructed Application Comparison

| Metric / Dimension | Original APK (`App134491.apk`) | Reconstructed Project (`reconstructed-project`) | Status |
|---|---|---|---|
| Package Identifier | `com.jyotin.ct` | `com.jyotin.ct` | Identical |
| App Label / Name | Child Tracker / CT | Child Tracker / CT | Identical |
| Min SDK / Target SDK | 21 / 30 | 21 / 30 | Identical |
| Source Language | Java | Java | Identical |
| Activities / Services | 8 Activities / 13 Services | 8 Activities / 13 Services | Identical |
| Resources & Drawables | Compiled Res | Decoded Res | Restored |
| Signing Certificate | Original Private Key | Debug Keystore | Re-signed with Debug Key |
| Buildability | Binary | Source Code & Gradle Build | Independently Buildable |
