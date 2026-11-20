# UI Reconstruction: com.jyotin.ct

## 1. UI Architecture
The user interface is built using standard Android XML Views and Material Components with Navigation Component (Fragments).

## 2. Key Screen Layouts
- `activity_main.xml` / `content_main.xml`: Host container for navigation graphs.
- `fragment_first.xml` / `fragment_second.xml`: Status and overview dashboards.
- `activity_login.xml`: Username, password input fields, sign in button.
- `activity_signup.xml`: Registration form fields.
- `activity_purpose.xml`: Agreement text and acceptance checkbox.
- `activity_permissions.xml`: Permission toggle checklist screen.
- `activity_autostart_screen.xml`: OEM Autostart permission wizard.
- `activity_sync.xml`: Synchronization status progress layout.
