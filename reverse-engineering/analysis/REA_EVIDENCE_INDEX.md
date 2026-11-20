# REA Evidence Index

## Evidence Artifacts Catalog

| Evidence ID | Analysis Stage | Source Path | Artifact Type | Summary |
|---|---|---|---|---|
| EVD-01 | APK Discovery | `reverse-engineering/input/original.apk` | Binary File | Base target APK |
| EVD-02 | Resource Extraction | `reverse-engineering/decompiled/apktool/` | XML & Assets | Decoded AndroidManifest.xml and res/ |
| EVD-03 | Decompilation | `reverse-engineering/decompiled/jadx/` | Java Sources | Decompiled Java source tree (com.jyotin.ct) |
| EVD-04 | Architecture Synthesis | `reverse-engineering/analysis/` | Markdown Docs | Technical architecture reports |
| EVD-05 | Project Reconstruction | `reverse-engineering/output/reconstructed-project/` | Gradle Project | Reconstructed Android Studio Project |
| EVD-06 | Build Output | `reverse-engineering/output/builds/reconstructed-debug.apk` | APK File | Rebuilt debug APK |
