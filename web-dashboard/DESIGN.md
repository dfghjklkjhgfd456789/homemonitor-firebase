# HomeMonitor Design System (DESIGN.md)

**Version:** 2.0.0  
**Scope:** `web-dashboard/`  
**Product Audience:** Ordinary parents and guardians seeking simple, transparent family safety and child protection.

---

## 1. Design Philosophy

HomeMonitor's design language combines three distinct reference philosophies:

*   **Apple-Inspired Restraint:** Content-first clarity, visual quietness, precise typography, subtle separation between surfaces, and purposeful animation.
*   **Intercom-Inspired Approachability:** Warm, inviting user interactions, helpful inline guidance, and reassuring status feedback.
*   **Notion-Inspired Organization:** Calm information hierarchy, uncluttered workspace structure, and readable data representation.

The dashboard must never look or feel like a military command center, developer console, hacker panel, or generic admin template.

---

## 2. Visual Atmosphere

The visual experience communicates:

*   **Calm & Reassuring:** Neutral background tones that reduce cognitive fatigue.
*   **Trustworthy & Secure:** High contrast, legible text, and unambiguous status indicators.
*   **Modern & Refined:** Balanced whitespace, consistent corner radii, and subtle 1px border separators.
*   **Parent-Friendly:** Non-technical terminology, intuitive step-by-step flows, and helpful micro-copy.

---

## 3. Brand Identity

*   **Primary Identity Icon:** `fa-shield-cat` (HomeMonitor Family Protection Shield).
*   **Brand Mission:** Simple, transparent family safety that empowers parents without technical complexity.
*   **Tone of Voice:** Empathetic, clear, direct, and reassuring.

---

## 4. Light Color Palette (Default Theme)

| Token | CSS Variable | Color Code | Purpose / Usage |
|---|---|---|---|
| Page background | `--hm-bg-page` | `#F6F6F3` | Calm neutral canvas for the entire web dashboard |
| Main surface | `--hm-bg-surface` | `#FFFFFF` | Primary cards, panels, forms, and modal containers |
| Secondary surface | `--hm-bg-subtle` | `#F1F2F4` | Secondary sidebars, well containers, and section fills |
| Hover surface | `--hm-bg-hover` | `#F7F8FA` | Interactive item hover background |
| Main text | `--hm-text-main` | `#1D1D1F` | Headlines, primary text, and high-emphasis labels |
| Secondary text | `--hm-text-secondary` | `#646B75` | Supporting descriptions, metadata, and labels |
| Muted text | `--hm-text-muted` | `#9299A3` | Secondary labels, captions, and timestamp text |
| Border | `--hm-border-subtle` | `#E4E6EA` | Structural card borders and divider lines |
| Primary action | `--hm-primary` | `#2468C8` | Primary buttons, active links, and selected states |
| Primary hover | `--hm-primary-hover` | `#1D55A5` | Primary action hover and active pressed state |
| Primary subtle | `--hm-primary-subtle` | `#EAF1FC` | Selected item fills and info callout backgrounds |
| Success | `--hm-success` | `#27834D` | Healthy connection states, online tags, positive status |
| Success bg | `--hm-success-subtle` | `#EAF5EE` | Success message alert container background |
| Warning | `--hm-warning` | `#A66A12` | Battery low, attention required, pending updates |
| Warning bg | `--hm-warning-subtle` | `#FFF4DE` | Warning message alert container background |
| Error / Destructive | `--hm-danger` | `#C43D45` | Critical errors, offline state, destructive actions |
| Error bg | `--hm-danger-subtle` | `#FCEEEF` | Error message alert container background |

---

## 5. Dark Color Palette (Dark Theme)

Activated via `[data-theme="dark"]` on `<html>` or `<body>`.

| Token | CSS Variable | Color Code | Purpose / Usage |
|---|---|---|---|
| Page background | `--hm-bg-page` | `#121417` | Main dark background canvas |
| Main surface | `--hm-bg-surface` | `#1A1E23` | Cards, panels, modals, and header surface |
| Secondary surface | `--hm-bg-subtle` | `#22272E` | Sidebar, secondary wells, and code blocks |
| Hover surface | `--hm-bg-hover` | `#2B323B` | Interactive row/button hover state |
| Main text | `--hm-text-main` | `#F3F4F6` | Headlines and high-contrast text |
| Secondary text | `--hm-text-secondary` | `#A7AFBA` | Supporting text and descriptions |
| Muted text | `--hm-text-muted` | `#858D99` | Captions, secondary timestamps |
| Border | `--hm-border-subtle` | `#313842` | Card borders and divider lines |
| Primary action | `--hm-primary` | `#7CB3FF` | Primary actions and highlighted links |
| Primary hover | `--hm-primary-hover` | `#599BFF` | Primary action hover state |
| Primary subtle | `--hm-primary-subtle` | `#1C2A3A` | Selected tab/row background |
| Success | `--hm-success` | `#30B46F` | Connected, healthy states |
| Success bg | `--hm-success-subtle` | `#142B20` | Success alert container fill |
| Warning | `--hm-warning` | `#E8B455` | Warning alerts and low battery |
| Warning bg | `--hm-warning-subtle` | `#2C2414` | Warning alert container fill |
| Error | `--hm-danger` | `#F0787E` | Error badges and remove actions |
| Error bg | `--hm-danger-subtle` | `#2E181A` | Error alert container fill |

---

## 6. Semantic Color Rules

Color must always carry specific, consistent meaning:

*   **Blue (`#2468C8` / `#7CB3FF`):** Interactive actions, navigation, active links, primary buttons.
*   **Green (`#27834D` / `#30B46F`):** Device online (<45s), healthy sync, success confirmations.
*   **Amber (`#A66A12` / `#E8B455`):** Device standby (>45s), low battery (<20%), action required.
*   **Red (`#C43D45` / `#F0787E`):** Connection lost/failed, destructive removal actions, critical alerts.
*   **Gray (`#646B75` / `#A7AFBA`):** Neutral offline status, inactive elements, secondary metadata.

> [!CAUTION]
> Never use color alone to communicate status. Always pair colored tags with text labels and FontAwesome icons (e.g. `<i class="fa-solid fa-circle-check"></i> Active`).

---

## 7. Typography System

**Font Family Stack:**  
`Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`

**Scale & Weights:**

| Level | Size | Line Height | Weight | Usage |
|---|---|---|---|---|
| Display / Heading 1 | 30px (1.875rem) | 1.25 | 600 (Semi-Bold) | Page titles, welcome header |
| Section / Heading 2 | 22px (1.375rem) | 1.3 | 600 (Semi-Bold) | Major section headings, modal titles |
| Card Title / Heading 3 | 17px (1.0625rem) | 1.35 | 600 (Semi-Bold) | Component card headers, metric titles |
| Body Main | 15px (0.9375rem) | 1.5 | 400 (Regular) | Primary text, descriptions, table contents |
| Secondary / Label | 14px (0.875rem) | 1.45 | 400–500 | Navigation links, form labels, input text |
| Caption / Micro | 12px (0.75rem) | 1.4 | 400 | Timestamps, secondary tags, helper copy |
| Button Label | 14px (0.875rem) | 1.2 | 500–600 | Action buttons, switches |

**Rules:**
*   Enable `font-variant-numeric: tabular-nums` for dates, counters, battery percentages, and metric values.
*   Maintain standard sentence casing across all headings and labels. Avoid aggressive ALL-CAPS except for micro badge headers.

---

## 8. Spacing, Corners & Elevation

**Spacing Scale (4px Grid):**
*   `--hm-space-1`: 4px
*   `--hm-space-2`: 8px
*   `--hm-space-3`: 12px
*   `--hm-space-4`: 16px
*   `--hm-space-6`: 24px
*   `--hm-space-8`: 32px
*   `--hm-space-10`: 40px
*   `--hm-space-12`: 48px

**Corner Radius Scale:**
*   Small Controls / Badges (`--hm-radius-sm`): **8px**
*   Buttons & Inputs (`--hm-radius-md`): **10px**
*   Standard Cards (`--hm-radius-lg`): **14px**
*   Large Panels & Hero Containers (`--hm-radius-xl`): **18px**
*   Modal Dialogs (`--hm-radius-modal`): **20px**
*   Status Pills (`--hm-radius-pill`): **9999px**

**Elevation & Shadows:**
*   `--hm-shadow-sm`: `0 1px 3px rgba(0, 0, 0, 0.05), 0 1px 2px rgba(0, 0, 0, 0.03)`
*   `--hm-shadow-md`: `0 4px 12px -2px rgba(0, 0, 0, 0.08), 0 2px 6px -1px rgba(0, 0, 0, 0.04)`
*   `--hm-shadow-lg`: `0 12px 32px -4px rgba(0, 0, 0, 0.12), 0 4px 12px -2px rgba(0, 0, 0, 0.06)`

---

## 9. Button & Interaction System

Each button variation must feature distinct visual states:

1.  **Primary Button (`.btn-hm-primary`):**  
    Solid blue fill (`#2468C8`), white text. Used for main call-to-action per section (e.g., "Add Child's Device", "Sync Now").
2.  **Secondary Button (`.btn-hm-secondary`):**  
    Subtle fill with border (`#F1F2F4` / border `#E4E6EA`). Used for supportive actions ("Cancel", "Refresh").
3.  **Quiet / Ghost Button (`.btn-hm-quiet`):**  
    Transparent background, subtle hover fill. Used inside toolbars and headers.
4.  **Destructive Button (`.btn-hm-danger`):**  
    Red subtle background or solid red (`#C43D45`). Used exclusively for device removal and session sign-out.

**Required Interaction States:**
*   **Hover:** Slight luminance adjustment (-5% lightness) with `--hm-shadow-sm`.
*   **Focus:** Visible 2px focus ring (`--hm-primary` with 3px offset).
*   **Pressed/Active:** Scale transform `scale(0.98)`.
*   **Disabled:** 45% opacity, `cursor: not-allowed`, no hover transforms.

---

## 10. Form Controls

*   **Labels:** Clear 14px medium text placed directly above inputs.
*   **Input Fields (`.form-control-hm`):** Height 42px, radius 10px, subtle 1px border.
*   **Focus Ring:** Highlighted border in primary color (`#2468C8`) with subtle 3px box-shadow glow.
*   **Password Inputs:** Grouped with inline toggle button (`<i class="fa-solid fa-eye"></i>`).
*   **Validation Errors:** Inline alert text directly below input field in error color (`#C43D45`).

---

## 11. Layout Architecture

```
+-------------------------------------------------------------------------------+
| TOP HEADER (.top-header)                                                      |
| [Device Name & Meta]                 [Theme Switcher]  [Sync Status] [Sync]   |
+-------------------------------------------------------------------------------+
| SIDEBAR (.sidebar)      | MAIN CANVAS (#tabPanelsContainer)                   |
| - Logo & Portal Title   | +-------------------------------------------------+ |
| - Monitored Device Sel  | | SECTION HEADER / ALERT NOTICE                   | |
| - Add Device Button     | +-------------------------------------------------+ |
| - FAMILY MANAGEMENT     | | METRIC SUMMARY CARDS GRID                       | |
|   * Home (Overview)     | | [ Total ]  [ Connected ]  [ Offline ] [ Alerts ]  | |
|   * Devices             | +-------------------------------------------------+ |
|   * Location            | | MAIN WORKSPACE CARDS / TABLES                   | |
|   * Screen Time         | |                                                 | |
|   * Family Activity     | |                                                 | |
|   * Safety Settings     | |                                                 | |
|   * Account             | |                                                 | |
|   * Help & Support      | |                                                 | |
| - ADMIN & BACKUP        | +-------------------------------------------------+ |
| - SIGN OUT              |                                                     |
+-------------------------------------------------------------------------------+
```

---

## 12. Parent-Friendly Terminology Map

Technical terminology must be completely replaced across all UI screens:

| Technical / Legacy Term | Parent-Friendly Replacement |
|---|---|
| Telemetry / Telemetry feed | Device activity / Activity logs |
| Heartbeat / Pulse | Last updated / Active check |
| RPC / C2 Command | Device request / Parent action |
| ICE failure / ICE disconnected | Couldn't connect directly |
| DataChannel ready | Direct connection ready |
| Unlink device | Remove device |
| Device unreachable | Device unavailable |
| Permission state | Device permissions |
| Sync failure / RTDB error | Couldn't update device information |
| P2P Direct Tunnel | Secure Direct Connection |
| Geofencing Area | Safe Zones & Geofences |
| Keylogger | Keystroke activity |

---

## 13. Motion and Micro-Interactions

*   **Transitions:** Standard duration `150ms` to `200ms` using `ease-in-out`.
*   **Sidebar Navigation Selection:** Smooth left accent border slide and background fade.
*   **Modal Appearance:** Scale transition from 95% to 100% with backdrop opacity fade.
*   **Reduced Motion:** Standard `@media (prefers-reduced-motion: reduce)` block disabling transforms and setting transition durations to `0.01ms`.

---

## 14. Responsive Breakpoints

*   **Desktop (`>= 1024px`):** Full 280px left sidebar, spacious 24px content grid.
*   **Tablet (`768px` to `1023px`):** Collapsible compact sidebar, 16px content padding.
*   **Mobile (`< 768px`):** Full-width mobile navigation drawer with hamburger trigger, stacked card layouts, and touch targets of at least 44px x 44px.

---

## 15. Implementation CSS Tokens Reference

```css
:root {
    /* Colors - Light Theme */
    --hm-bg-page: #F6F6F3;
    --hm-bg-surface: #FFFFFF;
    --hm-bg-subtle: #F1F2F4;
    --hm-bg-hover: #F7F8FA;
    --hm-text-main: #1D1D1F;
    --hm-text-secondary: #646B75;
    --hm-text-muted: #9299A3;
    --hm-border-subtle: #E4E6EA;
    --hm-primary: #2468C8;
    --hm-primary-hover: #1D55A5;
    --hm-primary-subtle: #EAF1FC;
    --hm-success: #27834D;
    --hm-success-subtle: #EAF5EE;
    --hm-warning: #A66A12;
    --hm-warning-subtle: #FFF4DE;
    --hm-danger: #C43D45;
    --hm-danger-subtle: #FCEEEF;

    /* Radii */
    --hm-radius-sm: 8px;
    --hm-radius-md: 10px;
    --hm-radius-lg: 14px;
    --hm-radius-xl: 18px;
    --hm-radius-modal: 20px;
    --hm-radius-pill: 9999px;

    /* Typography */
    --hm-font-family: 'Inter', system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}
```
