# Database and Storage Analysis: com.jyotin.ct

## 1. Storage Mechanisms
1. **SharedPreferences:**
   - Store session token, user ID, target configuration settings, sync intervals, and flags.
2. **Internal App Storage (`/data/data/com.jyotin.ct/files/` & `/cache/`):**
   - Temporary call recording audio files (`.mp3` / `.amr`).
   - Cached screenshot and camera image files.
3. **SQLite / Room Database:**
   - Decompiled models (`dbModels/*`) represent database entities used for queuing unsent telemetry records.
