#ifndef EGOLAND_WLR_HELPERS_H
#define EGOLAND_WLR_HELPERS_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <wayland-server-core.h>
#include <wayland-util.h>

#include <wlr/backend.h>
#include <wlr/render/wlr_renderer.h>
#include <wlr/types/wlr_compositor.h>
#include <wlr/types/wlr_scene.h>
#include <wlr/types/wlr_output.h>
#include <wlr/types/wlr_output_layout.h>
#include <wlr/types/wlr_input_device.h>
#include <wlr/types/wlr_keyboard.h>
#include <wlr/types/wlr_pointer.h>
#include <wlr/types/wlr_seat.h>
#include <wlr/types/wlr_cursor.h>
#include <wlr/types/wlr_xdg_shell.h>
#include <wlr/types/wlr_xdg_decoration_v1.h>
#include <wlr/types/wlr_layer_shell_v1.h>
#include <wlr/xwayland/xwayland.h>

struct wlr_shim_listener {
    struct wl_listener listener;
    void *user_data;
};

void wlr_shim_signal_add(struct wl_signal *signal, struct wlr_shim_listener *shim);
void wlr_shim_listener_remove(struct wlr_shim_listener *shim);
void *wlr_shim_listener_data(struct wl_listener *listener);

struct wlr_scene_tree *wlr_shim_scene_root_tree(struct wlr_scene *scene);
uint32_t wlr_shim_output_layout_box(struct wlr_output_layout *layout,
    int32_t *x, int32_t *y, int32_t *width, int32_t *height);
void wlr_shim_scene_node_position(struct wlr_scene_node *node,
    int32_t *x, int32_t *y);
struct wlr_scene_node *wlr_shim_scene_node_parent(struct wlr_scene_node *node);

struct wlr_scene_rect *wlr_shim_scene_rect_create(
    struct wlr_scene_tree *parent, int width, int height,
    float r, float g, float b, float a);
void wlr_shim_scene_rect_set_color(
    struct wlr_scene_rect *rect, float r, float g, float b, float a);

struct wlr_xdg_toplevel *wlr_shim_xdg_surface_toplevel(struct wlr_xdg_surface *surface);
struct wlr_xdg_popup *wlr_shim_xdg_surface_popup(struct wlr_xdg_surface *surface);
const char *wlr_shim_xdg_toplevel_title(struct wlr_xdg_toplevel *toplevel);
const char *wlr_shim_xdg_toplevel_app_id(struct wlr_xdg_toplevel *toplevel);
const char *wlr_shim_xwayland_surface_title(struct wlr_xwayland_surface *surface);
const char *wlr_shim_xwayland_display_name(struct wlr_xwayland *xwayland);
uint32_t wlr_shim_xdg_toplevel_set_size(struct wlr_xdg_toplevel *toplevel, int width, int height);

struct wl_signal *wlr_shim_backend_new_output(struct wlr_backend *backend);
struct wl_signal *wlr_shim_backend_new_input(struct wlr_backend *backend);
struct wl_signal *wlr_shim_backend_destroy(struct wlr_backend *backend);
struct wl_signal *wlr_shim_output_frame(struct wlr_output *output);
struct wl_signal *wlr_shim_output_destroy(struct wlr_output *output);
struct wl_signal *wlr_shim_xdg_shell_new_toplevel(struct wlr_xdg_shell *shell);
struct wl_signal *wlr_shim_layer_shell_new_surface(struct wlr_layer_shell_v1 *shell);
struct wl_signal *wlr_shim_layer_surface_destroy(struct wlr_layer_surface_v1 *surface);
uint32_t wlr_shim_layer_surface_layer(struct wlr_layer_surface_v1 *surface);
uint32_t wlr_shim_layer_surface_initialized(struct wlr_layer_surface_v1 *surface);
void wlr_shim_layer_surface_size(struct wlr_layer_surface_v1 *surface,
    uint32_t *width, uint32_t *height);
void wlr_shim_layer_surface_set_output(struct wlr_layer_surface_v1 *surface,
    struct wlr_output *output);
void wlr_shim_scene_layer_surface_configure(
    struct wlr_scene_layer_surface_v1 *scene_surface,
    struct wlr_box *full_area, struct wlr_box *usable_area);
struct wl_signal *wlr_shim_xdg_decoration_manager_new_toplevel(
    struct wlr_xdg_decoration_manager_v1 *manager);
struct wlr_xdg_toplevel *wlr_shim_xdg_toplevel_decoration_toplevel(
    struct wlr_xdg_toplevel_decoration_v1 *decoration);
struct wl_signal *wlr_shim_xdg_toplevel_decoration_destroy(
    struct wlr_xdg_toplevel_decoration_v1 *decoration);
struct wl_signal *wlr_shim_xdg_toplevel_decoration_request_mode(
    struct wlr_xdg_toplevel_decoration_v1 *decoration);
uint32_t wlr_shim_xdg_toplevel_decoration_requested_mode(
    struct wlr_xdg_toplevel_decoration_v1 *decoration);
void wlr_shim_xdg_toplevel_decoration_set_mode(
    struct wlr_xdg_toplevel_decoration_v1 *decoration, uint32_t mode);
struct wl_signal *wlr_shim_xdg_toplevel_destroy(struct wlr_xdg_toplevel *toplevel);
struct wl_signal *wlr_shim_xdg_toplevel_request_move(struct wlr_xdg_toplevel *toplevel);
struct wl_signal *wlr_shim_xdg_toplevel_request_maximize(struct wlr_xdg_toplevel *toplevel);
struct wl_signal *wlr_shim_xdg_toplevel_request_minimize(struct wlr_xdg_toplevel *toplevel);
uint32_t wlr_shim_xdg_toplevel_requested_maximized(struct wlr_xdg_toplevel *toplevel);
struct wl_signal *wlr_shim_xdg_surface_destroy(struct wlr_xdg_surface *surface);
struct wl_signal *wlr_shim_xdg_surface_new_popup(struct wlr_xdg_surface *surface);
struct wl_signal *wlr_shim_surface_commit(struct wlr_surface *surface);
struct wl_signal *wlr_shim_surface_map(struct wlr_surface *surface);
struct wl_signal *wlr_shim_surface_unmap(struct wlr_surface *surface);
struct wl_signal *wlr_shim_keyboard_key(struct wlr_keyboard *keyboard);
struct wl_signal *wlr_shim_keyboard_modifiers(struct wlr_keyboard *keyboard);
struct wl_signal *wlr_shim_cursor_motion(struct wlr_cursor *cursor);
struct wl_signal *wlr_shim_cursor_motion_absolute(struct wlr_cursor *cursor);
struct wl_signal *wlr_shim_cursor_button(struct wlr_cursor *cursor);
struct wl_signal *wlr_shim_cursor_axis(struct wlr_cursor *cursor);
struct wl_signal *wlr_shim_xwayland_ready(struct wlr_xwayland *xwayland);
struct wl_signal *wlr_shim_xwayland_new_surface(struct wlr_xwayland *xwayland);
struct wl_signal *wlr_shim_xwayland_surface_destroy(struct wlr_xwayland_surface *surface);
struct wl_signal *wlr_shim_xwayland_surface_request_activate(struct wlr_xwayland_surface *surface);
struct wl_signal *wlr_shim_xwayland_surface_request_move(struct wlr_xwayland_surface *surface);

uint32_t wlr_shim_input_device_type(struct wlr_input_device *device);
struct wlr_keyboard *wlr_shim_keyboard_from_device(struct wlr_input_device *device);
struct wlr_xdg_surface *wlr_shim_xdg_toplevel_base(struct wlr_xdg_toplevel *toplevel);
struct wlr_xdg_surface *wlr_shim_xdg_popup_base(struct wlr_xdg_popup *popup);
void wlr_shim_xdg_popup_geometry(struct wlr_xdg_surface *surface,
    int32_t *x, int32_t *y, int32_t *width, int32_t *height);
struct wlr_surface *wlr_shim_xdg_surface_get_surface(struct wlr_xdg_surface *surface);
void wlr_shim_xdg_surface_geometry(struct wlr_xdg_surface *surface,
    int32_t *x, int32_t *y, int32_t *width, int32_t *height);
uint32_t wlr_shim_xdg_surface_role(struct wlr_xdg_surface *surface);
uint32_t wlr_shim_xdg_surface_initialized(struct wlr_xdg_surface *surface);
uint32_t wlr_shim_xdg_surface_schedule_configure_if_ready(
    struct wlr_xdg_surface *surface);
struct wlr_surface *wlr_shim_xwayland_surface_get_surface(struct wlr_xwayland_surface *surface);
int wlr_shim_xwayland_surface_override_redirect(struct wlr_xwayland_surface *surface);
int16_t wlr_shim_xwayland_surface_x(struct wlr_xwayland_surface *surface);
int16_t wlr_shim_xwayland_surface_y(struct wlr_xwayland_surface *surface);
uint16_t wlr_shim_xwayland_surface_width(struct wlr_xwayland_surface *surface);
uint16_t wlr_shim_xwayland_surface_height(struct wlr_xwayland_surface *surface);
const char *wlr_shim_output_name(struct wlr_output *output);

double wlr_shim_motion_dx(void *data);
double wlr_shim_motion_dy(void *data);
uint32_t wlr_shim_motion_time(void *data);
struct wlr_input_device *wlr_shim_motion_device(void *data);
double wlr_shim_motion_absolute_x(void *data);
double wlr_shim_motion_absolute_y(void *data);
uint32_t wlr_shim_motion_absolute_time(void *data);
struct wlr_input_device *wlr_shim_motion_absolute_device(void *data);
uint32_t wlr_shim_button_event_button(void *data);
uint32_t wlr_shim_button_event_state(void *data);
uint32_t wlr_shim_button_event_time(void *data);
void wlr_shim_axis_event(void *data, uint32_t *time, uint32_t *source,
    uint32_t *orientation, uint32_t *relative_direction,
    double *delta, int32_t *delta_discrete);
void wlr_shim_seat_notify_axis(struct wlr_seat *seat, uint32_t time,
    uint32_t orientation, double delta, int32_t delta_discrete,
    uint32_t source, uint32_t relative_direction);
uint32_t wlr_shim_key_event_keycode(void *data);
uint32_t wlr_shim_key_event_state(void *data);
uint32_t wlr_shim_key_event_time(void *data);

void wlr_shim_seat_notify_modifiers(struct wlr_seat *seat,
    uint32_t depressed, uint32_t latched, uint32_t locked, uint32_t group);

#endif
