# Network and API Analysis: com.jyotin.ct

## 1. Network Stack
- **HTTP Client:** OkHttp3 / HttpURLConnection / Apache HTTP Legacy
- **Cloud Messaging:** Firebase Cloud Messaging (FCM)
- **Traffic Protocol:** HTTP / HTTPS cleartext allowed (`android:usesCleartextTraffic="true"`)

## 2. Conceptual API Operations
- `POST /api/login` - Authenticate user credentials
- `POST /api/register` - Create user profile
- `POST /api/sync/device` - Upload device info & hardware stats
- `POST /api/sync/location` - Upload location coordinates
- `POST /api/sync/calls` - Upload call log data
- `POST /api/sync/recordings` - Upload audio call recording binary files
- `POST /api/sync/messages` - Upload SMS / messaging notifications
- `POST /api/sync/usage` - Upload application usage statistics
- `POST /api/sync/accessibility` - Upload keylog and screen text data
