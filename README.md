# EgolandWM

EgolandWM 是一个基于 **Kotlin/Native + wlroots 0.20** 的实验性 Wayland compositor / 浮动窗口管理器。

它使用 wlroots 处理后端、渲染、输出和输入设备，Kotlin 侧负责窗口管理、协议对象生命周期、输入状态机以及配置逻辑。项目目标是提供一个结构清晰、可实验的最小 compositor，而不是完整的桌面环境。

## 功能

- Wayland backend 自动探测；支持 DRM、嵌套 Wayland、headless 等 wlroots 后端。
- `wlr_scene` 场景图渲染与多输出管理。
- XDG shell top-level：
  - map/unmap/destroy 生命周期。
  - 窗口焦点、提升层级、关闭。
  - 窗口移动与八方向缩放。
  - 处理 window geometry 偏移，输入坐标换算为 surface-local 坐标。
- XDG popup：
  - 右键菜单、下拉菜单等 popup。
  - 嵌套 popup。
  - popup 几何跟踪、指针命中与 pointer grab。
- 客户自绘装饰 CSD：
  - `xdg_toplevel.request_move`
  - `request_maximize`
  - `request_minimize`
  - `Super + M` 恢复最近最小化窗口。
- `xdg-decoration`：
  - `client`：默认不绘制 compositor 装饰。
  - `server`：绘制 compositor 装饰。
  - `auto`：遵循客户端请求，未请求时按 client 处理。
- `layer-shell`：
  - `background`、`bottom`、`top`、`overlay` 四层。
  - 基础 configure、map/unmap/destroy。
  - 按 layer 层级参与指针命中。
- 输入：
  - XKB keymap、modifier、CapsLock 等锁定修饰键。
  - 相对和绝对鼠标移动。
  - 鼠标按键、滚轮、pointer enter/motion/button/axis/frame。
  - pointer focus 去重，避免重复 enter 打断菜单 grab。
- 剪贴板与拖放：
  - `wl_data_device_manager`。
  - selection 设置与读取。
  - pointer drag and drop。
  - 拖拽时目标窗口轻微提亮。
- XWayland：可选启用。
- 配置：
  - 装饰行为。
  - autostart 命令。
  - XWayland 开关。
- 收到 `SIGINT` / `SIGTERM` 时优雅退出。

## 环境要求

Linux x86_64 环境，并需要以下依赖：

- JDK 21 或与 Kotlin/Native 2.4.21 兼容的 JDK。
- wlroots 0.20 开发库。
- Wayland server 库。
- libxkbcommon。
- libdrm。
- Pixman。
- XCB、XCB EWMH、XCB ICCCM。

构建配置默认使用以下头文件路径：

```text
/usr/include
/usr/include/wlroots-0.20
/usr/include/pixman-1
/usr/include/libdrm
```

`wlroots.def` 默认链接：

```text
-lwlroots-0.20 -lwayland-server -lxkbcommon -lxcb -lxcb-ewmh -lxcb-icccm -lpixman-1
```

仓库内包含 layer-shell 协议 XML 和生成的协议头：

```text
src/linuxMain/cinterop/wlr-layer-shell-unstable-v1.xml
src/linuxMain/cinterop/wlr-layer-shell-unstable-v1-protocol.h
```

若需要重新生成协议头，可使用：

```bash
wayland-scanner client-header \
  src/linuxMain/cinterop/wlr-layer-shell-unstable-v1.xml \
  src/linuxMain/cinterop/wlr-layer-shell-unstable-v1-protocol.h
```

## 构建

使用 Gradle wrapper：

```bash
./gradlew build --no-daemon
```

如果修改了 C shim 或 cinterop 头文件，需要重新生成绑定：

```bash
./gradlew :cinteropWlrootsLinuxX64 --rerun-tasks --no-daemon
./gradlew build --no-daemon
```

构建产物：

```text
build/bin/linuxX64/debugExecutable/EgolandWM.kexe
build/bin/linuxX64/releaseExecutable/EgolandWM.kexe
```

## 运行

### 直接运行

在具备 DRM 权限的 TTY 或图形环境中：

```bash
./build/bin/linuxX64/debugExecutable/EgolandWM.kexe
```

### 嵌套在已有 Wayland 合成器中

Gradle 的 `run` 任务默认使用 `WLR_BACKENDS=wayland`：

```bash
./gradlew run --no-daemon
```

也可以手动运行：

```bash
WLR_BACKENDS=wayland ./build/bin/linuxX64/debugExecutable/EgolandWM.kexe
```

### headless 测试

```bash
env -u WAYLAND_DISPLAY -u DISPLAY \
  WLR_BACKENDS=headless \
  ./build/bin/linuxX64/debugExecutable/EgolandWM.kexe
```

启动成功后会打印：

```text
egoland: running on WAYLAND_DISPLAY=wayland-0
egoland: new output HEADLESS-1
```

启动客户端时必须使用该 compositor 自己的 socket：

```bash
WAYLAND_DISPLAY=wayland-0 weston-terminal
```

注意：compositor 内部执行 `setenv("WAYLAND_DISPLAY", ...)` 只影响自身进程，不会改变当前 shell 的环境变量。外层 `WAYLAND_DISPLAY` 仅用于承载嵌套运行的 EgolandWM 本身。

## 默认快捷键

| 快捷键 | 行为 |
| --- | --- |
| `Super + P` | 关闭当前焦点窗口 |
| `Super + M` | 恢复最近最小化窗口 |

快捷键使用 evdev keycode：`P = 17`，`M = 50`。

## 配置文件

默认读取当前工作目录下的：

```text
egoland.conf
```

也可以通过环境变量指定配置文件：

```bash
EGOLAND_CONFIG=/path/to/egoland.conf ./build/bin/linuxX64/debugExecutable/EgolandWM.kexe
```

默认配置：

```ini
# 装饰行为：client | server | auto
decoration = client

# 启动后自动运行的命令，可写多行
autostart =

# 是否启用 XWayland
xwayland = true
```

说明：

- `decoration = client`：默认不绘制 compositor 蓝条。
- `decoration = server`：对窗口启用 compositor 装饰。
- `decoration = auto`：遵循客户端请求；未请求时按 client 处理。
- `autostart` 在当前 compositor 的 `WAYLAND_DISPLAY` 设置完成后通过 shell 后台执行。
- 配置文件应视为可信本地文件，因为 autostart 会调用 shell。

## 项目结构

```text
src/linuxMain/kotlin/reverie/egoland/
├── main.kt                       # 入口与信号
├── Config.kt                     # 配置解析
├── Server.kt                     # compositor 初始化与资源装配
├── Window.kt                     # 统一窗口对象
└── protocol/
    ├── OutputManager.kt          # 输出与帧回调
    ├── WindowManager.kt          # XDG shell、popup、窗口栈
    ├── XdgDecorationManager.kt   # xdg-decoration
    ├── InputManager.kt           # wl_seat 输入与交互状态机
    ├── LayerManager.kt           # layer-shell
    ├── DataDeviceManager.kt      # clipboard / drag and drop
    ├── XWaylandManager.kt        # XWayland
    └── SceneUtil.kt              # scene 节点工具

src/linuxMain/cinterop/
├── wlroots.def                   # cinterop 定义
├── wlr_helpers.h                 # C shim 声明
├── wlr_helpers.c                 # C shim 实现
├── wlr-layer-shell-unstable-v1.xml
└── wlr-layer-shell-unstable-v1-protocol.h
```

`SignalListener` 是 Kotlin 回调接入 wlroots `wl_signal` 的核心工具；C shim 用于包装宏、私有字段以及 Kotlin/Native 不便于直接访问的结构体字段。

## 已知限制

- 实验性实现，不是生产级桌面环境。
- 未实现 primary selection。
- 未实现触摸输入。
- layer-shell 为基础实现，不做完整 exclusive zone 可用区计算。
- 没有运行时 IPC 或动态重载配置，修改配置后需要重启 compositor。
- 默认使用系统默认 XKB keymap，不支持在配置文件中自定义布局。
- cinterop 路径与系统 wlroots 0.20 头文件布局相关。

## 验证

常用构建与冒烟测试：

```bash
./gradlew build --no-daemon

env -u WAYLAND_DISPLAY -u DISPLAY \
  WLR_BACKENDS=headless \
  ./build/bin/linuxX64/debugExecutable/EgolandWM.kexe
```

预期日志包含：

```text
egoland: running on WAYLAND_DISPLAY=wayland-0
egoland: new output HEADLESS-1
egoland: shutdown
```

## License

MIT License。详见 `LICENSE`。
