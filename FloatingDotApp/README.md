# ShieldAi - Project Documentation

Welcome to the **ShieldAi** project. This documentation is designed for the team to understand the technical architecture and how to collaborate effectively using AI agents.

---

## 1. Project Overview
ShieldAi is an Android application that features a "Floating Red Dot" mechanism. 
- **The Core Goal**: When the app is minimized (background), a movable red dot appears on top of all other apps.
- **Functionality**: Tapping the red dot instantly restores the application to the foreground.

---

## 2. Technical Architecture (for C/System Programmers)
If you are coming from a C background, think of this app as a **Main Event Loop** interacting with a **Window Manager Service**.

### Key Components:
1. **`MainActivity.kt` (The UI Controller)**
   - Manages the **Life Cycle**: 
     - `onStart()` -> Destroys the floating widget (app is now in foreground).
     - `onStop()` -> Spawns the floating widget (app is now in background).
   - Handles **Runtime Permissions**: Uses `Settings.canDrawOverlays` to request system-level drawing authority.

2. **`FloatingWidgetService.kt` (The Overlay Engine)**
   - This is a background `Service`. It doesn't have a standard UI window; instead, it injects a view directly into the Android **WindowManager**.
   - **Mechanism**: It uses `TYPE_APPLICATION_OVERLAY`. In C terms, this is like creating a top-level window with the highest Z-order.
   - **Touch Logic**: Implements an `OnTouchListener`. It calculates the `(dx, dy)` offset during `ACTION_MOVE` and updates the window parameters in real-time.

3. **`AndroidManifest.xml` (The Header/Linker Config)**
   - Defines the `SYSTEM_ALERT_WINDOW` permission.
   - Declares the Service and Activity components.

---

## 3. How to Run & Test
1. **Clone the Repo**: Ensure you have cloned `https://github.com/Reorad/ShieldAi.git`.
2. **Build**: Click the **Sync Project with Gradle Files** icon in Android Studio.
3. **Run**: Use the **Green Play Button**.
4. **Permission**: On first launch, the app will ask for "Display over other apps". **Enable ShieldAi**.
5. **The "Dot" Test**: Open the app, then press the **Home button**. The Red Dot should appear. Drag it, then tap it to return to ShieldAi.

---

## 4. Team Collaboration Workflow
Since we have 4 members and use AI Agents, follow this flow:

### Git Commands (Use Terminal):
- **Get Updates**: `git pull origin main` (Do this every time you start working).
- **Save Work**: 
  1. `git add .`
  2. `git commit -m "Description of what you did"`
  3. `git push origin main`

### Using AI Agents:
- **Context**: Your agent reads the local files. If a teammate pushes new code, `pull` it first so your agent knows about it.
- **Tasks**: Assign specific features to each person. For example:
  - *Member 1*: Background security logic.
  - *Member 2*: UI/UX Polish.
  - *Member 3*: Notification system.
- **Code Audit**: Ask your agent: *"Explain the changes my teammate made in the last commit"* to stay in sync.

---

## 5. File Map
- `app/src/main/java/.../MainActivity.kt`: Main logic & Lifecycle.
- `app/src/main/java/.../FloatingWidgetService.kt`: Red dot movement & drawing.
- `app/src/main/res/layout/`: UI XML files.
- `app/src/main/res/drawable/`: Assets (Lock icon, Red dot shape).

---
**ShieldAi - Security through Visibility.**
