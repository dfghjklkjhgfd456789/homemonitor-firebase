# Missing Functionality & Backend Limitations

## 1. Remote Server Dependencies
- The original application relies on a remote C2 / telemetry server endpoint for user authentication (`/api/login`), user registration (`/api/register`), and telemetry data ingestion (`/api/sync/*`).
- Live network operations against the original remote servers were not executed to avoid unauthorized backend interactions.

## 2. Dynamic Resources
- Original FCM project keys depend on the original Google Play Services `google-services.json` server configuration.
