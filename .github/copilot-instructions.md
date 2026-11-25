# EgolandWM Repository Instructions

## Project summary

EgolandWM is a Linux-only Wayland compositor implemented in Kotlin/Native. It uses wlroots 0.20 and X11/XWayland support to provide XDG-shell surfaces, popups, outputs, keyboard/pointer input, focus, move/resize, maximize/minimize, and server-side decorations.

## Build and run

- Build and link debug/release binaries:
  - `./gradlew build`
  - `./gradlew linkDebugExecutableLinuxX64`
  - `./gradlew linkReleaseExecutableLinuxX64`
- Run the debug executable through Gradle:
  - `./gradlew runDebugExecutableLinuxX64`
- The Gradle executable run task sets `WLR_BACKENDS=wayland` for the Wayland backend.
- Clean outputs: `./gradlew clean`
- There is currently no test source set; `./gradlew linuxX64Test` reports no tests and `./gradlew build` skips native test execution.
- No lint or formatting task is currently configured.

The build requires Linux and the native dependencies listed in `README.md`, especially wlroots 0.20 headers under `/usr/include/wlroots-0.20`. Native libraries are declared in `src/linuxMain/cinterop/wlroots.def`; build failures can indicate missing headers or libraries rather than Kotlin errors.

At runtime, `EGOLAND_CONFIG` selects a config file; otherwise `egoland.conf` in the working directory is used. The executable needs an active wlroots backend and should be run from a Linux graphical session or Wayland nested environment.

## Architecture

- `src/linuxMain/kotlin/reverie/egoland/main.kt` installs SIGINT/SIGTERM handlers and owns the top-level `Server` lifecycle.
- `Server.kt` creates the wlroots display, backend, renderer, allocator, scene, output layout, compositor, XDG shell, seat, and sub-managers. `start()`, `run()`, and `cleanup()` are the lifecycle entry points.
- `OutputManager.kt` handles output creation/configuration and scene-output frame callbacks.
- `WindowManager.kt` owns XDG toplevel and popup surfaces, focus and Z-order, decoration requests/configuration, and XDG surface listener lifecycles.
- `Window.kt` is the shared XDG/XWayland window model and geometry/hit-test representation, including optional compositor decorations.
- `InputManager.kt` owns the cursor, keyboard/pointer devices, hit testing, pointer focus, and move/resize interactions.
- `XWaylandManager.kt` bridges X11 surfaces into the same `WindowManager`/`Window` model.
- `Config.kt` implements a small key/value config parser and supplies decoration, autostart, and XWayland settings.
- `src/linuxMain/cinterop/wlr_helpers.{c,h}` exposes wlroots APIs that are awkward or impossible to access directly from the Kotlin cinterop surface; `wlroots.def` is the cinterop entry point and also defines linker arguments.

wlroots callbacks enter Kotlin through `SignalListener` in `Server.kt`. It wraps native `wl_listener` callbacks with Kotlin `staticCFunction` dispatch and `StableRef` callback storage.

## Repository conventions

- Kotlin code uses `@file:OptIn(ExperimentalForeignApi::class)` and imports cinterop types directly from `kotlinx.cinterop`; retain this pattern when adding native-pointer code.
- Keep native pointer ownership explicit. Objects whose Kotlin code must stay alive across callbacks use `StableRef`; disconnect every `SignalListener` and dispose every idle callback/ref when the owning surface, window, manager, or server is destroyed.
- `wlr_shim_*` helpers are the compatibility boundary for private or awkward wlroots C structures. When changing a wlroots API interaction, update the C shim and header together with the Kotlin call and cinterop include/link configuration.
- New wlroots signal wiring should generally follow `SignalListener.connect(...)` plus `onSignal = { _, data -> ... }`, and disposal should disconnect listeners before destroying native owners.
- XDG and XWayland surfaces both map to `Window`. Preserve the existing split: protocol-specific setup belongs in `WindowManager` or `XWaylandManager`, while geometry, activation, close/ping, and hit testing belong in `Window`.
- Scene nodes represent compositor-owned rendering. Use `wlr_scene_node_set_position`, `wlr_scene_node_set_enabled`, and `wlr_scene_node_raise_to_top` consistently; decoration offsets are handled by `Window.decorOffset`.
- Surface geometry and pointer coordinates use the existing offset conventions: XDG content excludes compositor decoration, while XWayland geometry includes `decorOffset` where appropriate. Update `configureGeometry()` and pointer coordinate conversion together if these conventions change.
- Config keys are lowercase and parsed from `key = value` lines; repeated `autostart` keys append commands. `EGOLAND_CONFIG` overrides the default `egoland.conf` path.
- The Super-key bindings are currently keycodes `17` (close focused window) and `50` (restore last minimized window) in `InputManager.kt`; retain or document keycode behavior carefully rather than assuming stable named keys.
