# EgolandWM

A small Wayland compositor written in Kotlin/Native and built with wlroots.

EgolandWM hosts native Wayland applications through XDG-shell and X11 applications through XWayland. It includes basic multi-output support, window focus, move and resize operations, decorations, maximize/minimize handling, keyboard input, pointer input, and popup surfaces.

> This is an experimental compositor and is not intended to replace a production desktop environment.

## Features

- wlroots-backed Wayland compositor
- XDG-shell top-level and popup support
- Optional XWayland support
- Multiple monitor/output support
- Move and resize windows
- Server-side or client-side decoration modes
- Maximize and minimize support
- Keyboard and pointer input
- Configurable autostart commands
- SIGINT and SIGTERM shutdown handling

## Requirements

EgolandWM expects a Linux environment with Kotlin/Native and the following native dependencies:

- wlroots 0.20
- Wayland server libraries
- libxkbcommon
- XCB
- XCB EWMH and ICCCM
- Pixman
- libdrm
- Gradle 9.9 or the included Gradle wrapper
- JDK compatible with the installed Kotlin Gradle plugin

The build configuration expects wlroots headers at:

```text
/usr/include/wlroots-0.20
```
