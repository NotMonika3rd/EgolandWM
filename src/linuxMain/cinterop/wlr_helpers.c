#include "wlr_helpers.h"

void wlr_shim_signal_add(struct wl_signal *signal, struct wlr_shim_listener *shim) {
    wl_list_init(&shim->listener.link);
    wl_signal_add(signal, &shim->listener);
}

void wlr_shim_listener_remove(struct wlr_shim_listener *shim) {
    wl_list_remove(&shim->listener.link);
}

void *wlr_shim_listener_data(struct wl_listener *listener) {
    struct wlr_shim_listener *shim = wl_container_of(listener, shim, listener);
    return shim->user_data;
}

struct wlr_scene_tree *wlr_shim_scene_root_tree(struct wlr_scene *scene) {
    return &scene->tree;
}

uint32_t wlr_shim_output_layout_box(struct wlr_output_layout *layout,
        int32_t *x, int32_t *y, int32_t *width, int32_t *height) {
    struct wlr_box box;
    wlr_output_layout_get_box(layout, NULL, &box);
    *x = box.x;
    *y = box.y;
    *width = box.width;
    *height = box.height;
    return box.width > 0 && box.height > 0 ? 1u : 0u;
}

void wlr_shim_scene_node_position(struct wlr_scene_node *node,
        int32_t *x, int32_t *y) {
    *x = node->x;
    *y = node->y;
}

struct wlr_scene_node *wlr_shim_scene_node_parent(struct wlr_scene_node *node) {
    return node->parent ? &node->parent->node : NULL;
}

struct wlr_scene_rect *wlr_shim_scene_rect_create(
        struct wlr_scene_tree *parent, int width, int height,
        float r, float g, float b, float a) {
    float color[4] = {r, g, b, a};
    return wlr_scene_rect_create(parent, width, height, color);
}

void wlr_shim_scene_rect_set_color(
        struct wlr_scene_rect *rect, float r, float g, float b, float a) {
    float color[4] = {r, g, b, a};
    wlr_scene_rect_set_color(rect, color);
}

struct wlr_xdg_toplevel *wlr_shim_xdg_surface_toplevel(struct wlr_xdg_surface *surface) {
    return surface->toplevel;
}

struct wlr_xdg_popup *wlr_shim_xdg_surface_popup(struct wlr_xdg_surface *surface) {
    return surface->popup;
}

const char *wlr_shim_xdg_toplevel_title(struct wlr_xdg_toplevel *toplevel) {
    return toplevel->title;
}

const char *wlr_shim_xdg_toplevel_app_id(struct wlr_xdg_toplevel *toplevel) {
    return toplevel->app_id;
}

const char *wlr_shim_xwayland_surface_title(struct wlr_xwayland_surface *surface) {
    return surface->title;
}

const char *wlr_shim_xwayland_display_name(struct wlr_xwayland *xwayland) {
    return xwayland->display_name;
}

uint32_t wlr_shim_xdg_toplevel_set_size(struct wlr_xdg_toplevel *toplevel, int width, int height) {
    return wlr_xdg_toplevel_set_size(toplevel, width, height);
}

struct wl_signal *wlr_shim_backend_new_output(struct wlr_backend *backend) {
    return &backend->events.new_output;
}

struct wl_signal *wlr_shim_backend_new_input(struct wlr_backend *backend) {
    return &backend->events.new_input;
}

struct wl_signal *wlr_shim_backend_destroy(struct wlr_backend *backend) {
    return &backend->events.destroy;
}

struct wl_signal *wlr_shim_output_frame(struct wlr_output *output) {
    return &output->events.frame;
}

struct wl_signal *wlr_shim_output_destroy(struct wlr_output *output) {
    return &output->events.destroy;
}

struct wl_signal *wlr_shim_xdg_shell_new_toplevel(struct wlr_xdg_shell *shell) {
    return &shell->events.new_toplevel;
}

struct wl_signal *wlr_shim_xdg_toplevel_destroy(struct wlr_xdg_toplevel *toplevel) {
    return &toplevel->events.destroy;
}

struct wl_signal *wlr_shim_xdg_toplevel_request_move(struct wlr_xdg_toplevel *toplevel) {
    return &toplevel->events.request_move;
}

struct wl_signal *wlr_shim_xdg_toplevel_request_maximize(struct wlr_xdg_toplevel *toplevel) {
    return &toplevel->events.request_maximize;
}

struct wl_signal *wlr_shim_xdg_toplevel_request_minimize(struct wlr_xdg_toplevel *toplevel) {
    return &toplevel->events.request_minimize;
}

uint32_t wlr_shim_xdg_toplevel_requested_maximized(struct wlr_xdg_toplevel *toplevel) {
    return toplevel->requested.maximized ? 1u : 0u;
}

struct wl_signal *wlr_shim_xdg_decoration_manager_new_toplevel(
        struct wlr_xdg_decoration_manager_v1 *manager) {
    return &manager->events.new_toplevel_decoration;
}

struct wlr_xdg_toplevel *wlr_shim_xdg_toplevel_decoration_toplevel(
        struct wlr_xdg_toplevel_decoration_v1 *decoration) {
    return decoration->toplevel;
}

struct wl_signal *wlr_shim_xdg_toplevel_decoration_destroy(
        struct wlr_xdg_toplevel_decoration_v1 *decoration) {
    return &decoration->events.destroy;
}

struct wl_signal *wlr_shim_xdg_toplevel_decoration_request_mode(
        struct wlr_xdg_toplevel_decoration_v1 *decoration) {
    return &decoration->events.request_mode;
}

uint32_t wlr_shim_xdg_toplevel_decoration_requested_mode(
        struct wlr_xdg_toplevel_decoration_v1 *decoration) {
    return (uint32_t)decoration->requested_mode;
}

void wlr_shim_xdg_toplevel_decoration_set_mode(
        struct wlr_xdg_toplevel_decoration_v1 *decoration, uint32_t mode) {
    if (decoration->toplevel == NULL || !decoration->toplevel->base->initialized) return;
    wlr_xdg_toplevel_decoration_v1_set_mode(decoration,
        (enum wlr_xdg_toplevel_decoration_v1_mode)mode);
}

struct wl_signal *wlr_shim_xdg_surface_destroy(struct wlr_xdg_surface *surface) {
    return &surface->events.destroy;
}

struct wl_signal *wlr_shim_xdg_surface_new_popup(struct wlr_xdg_surface *surface) {
    return &surface->events.new_popup;
}

struct wl_signal *wlr_shim_surface_commit(struct wlr_surface *surface) {
    return &surface->events.commit;
}

struct wl_signal *wlr_shim_surface_map(struct wlr_surface *surface) {
    return &surface->events.map;
}

struct wl_signal *wlr_shim_surface_unmap(struct wlr_surface *surface) {
    return &surface->events.unmap;
}

struct wl_signal *wlr_shim_keyboard_key(struct wlr_keyboard *keyboard) {
    return &keyboard->events.key;
}

struct wl_signal *wlr_shim_keyboard_modifiers(struct wlr_keyboard *keyboard) {
    return &keyboard->events.modifiers;
}

struct wl_signal *wlr_shim_cursor_motion(struct wlr_cursor *cursor) {
    return &cursor->events.motion;
}

struct wl_signal *wlr_shim_cursor_motion_absolute(struct wlr_cursor *cursor) {
    return &cursor->events.motion_absolute;
}

struct wl_signal *wlr_shim_cursor_button(struct wlr_cursor *cursor) {
    return &cursor->events.button;
}

struct wl_signal *wlr_shim_xwayland_ready(struct wlr_xwayland *xwayland) {
    return &xwayland->events.ready;
}

struct wl_signal *wlr_shim_xwayland_new_surface(struct wlr_xwayland *xwayland) {
    return &xwayland->events.new_surface;
}

struct wl_signal *wlr_shim_xwayland_surface_destroy(struct wlr_xwayland_surface *surface) {
    return &surface->events.destroy;
}

struct wl_signal *wlr_shim_xwayland_surface_request_activate(struct wlr_xwayland_surface *surface) {
    return &surface->events.request_activate;
}

struct wl_signal *wlr_shim_xwayland_surface_request_move(struct wlr_xwayland_surface *surface) {
    return &surface->events.request_move;
}

uint32_t wlr_shim_input_device_type(struct wlr_input_device *device) {
    return (uint32_t)device->type;
}

struct wlr_keyboard *wlr_shim_keyboard_from_device(struct wlr_input_device *device) {
    return wlr_keyboard_from_input_device(device);
}

struct wlr_xdg_surface *wlr_shim_xdg_toplevel_base(struct wlr_xdg_toplevel *toplevel) {
    return toplevel->base;
}

struct wlr_xdg_surface *wlr_shim_xdg_popup_base(struct wlr_xdg_popup *popup) {
    return popup->base;
}

void wlr_shim_xdg_popup_geometry(struct wlr_xdg_surface *surface,
        int32_t *x, int32_t *y, int32_t *width, int32_t *height) {
    *x = surface->current.geometry.x;
    *y = surface->current.geometry.y;
    *width = surface->current.geometry.width;
    *height = surface->current.geometry.height;
}

void wlr_shim_xdg_surface_geometry(struct wlr_xdg_surface *surface,
        int32_t *x, int32_t *y, int32_t *width, int32_t *height) {
    wlr_shim_xdg_popup_geometry(surface, x, y, width, height);
}

struct wlr_surface *wlr_shim_xdg_surface_get_surface(struct wlr_xdg_surface *surface) {
    return surface->surface;
}

uint32_t wlr_shim_xdg_surface_role(struct wlr_xdg_surface *surface) {
    return (uint32_t)surface->role;
}

uint32_t wlr_shim_xdg_surface_schedule_configure_if_ready(
        struct wlr_xdg_surface *surface) {
    if (!surface->initialized) return 0u;
    return wlr_xdg_surface_schedule_configure(surface);
}

struct wlr_surface *wlr_shim_xwayland_surface_get_surface(struct wlr_xwayland_surface *surface) {
    return surface->surface;
}

int wlr_shim_xwayland_surface_override_redirect(struct wlr_xwayland_surface *surface) {
    return surface->override_redirect ? 1 : 0;
}

int16_t wlr_shim_xwayland_surface_x(struct wlr_xwayland_surface *surface) {
    return surface->x;
}

int16_t wlr_shim_xwayland_surface_y(struct wlr_xwayland_surface *surface) {
    return surface->y;
}

uint16_t wlr_shim_xwayland_surface_width(struct wlr_xwayland_surface *surface) {
    return surface->width;
}

uint16_t wlr_shim_xwayland_surface_height(struct wlr_xwayland_surface *surface) {
    return surface->height;
}

const char *wlr_shim_output_name(struct wlr_output *output) {
    return output->name;
}

double wlr_shim_motion_dx(void *data) {
    return ((struct wlr_pointer_motion_event *)data)->delta_x;
}

double wlr_shim_motion_dy(void *data) {
    return ((struct wlr_pointer_motion_event *)data)->delta_y;
}

uint32_t wlr_shim_motion_time(void *data) {
    return ((struct wlr_pointer_motion_event *)data)->time_msec;
}

struct wlr_input_device *wlr_shim_motion_device(void *data) {
    struct wlr_pointer *pointer = ((struct wlr_pointer_motion_event *)data)->pointer;
    return &pointer->base;
}

double wlr_shim_motion_absolute_x(void *data) {
    return ((struct wlr_pointer_motion_absolute_event *)data)->x;
}

double wlr_shim_motion_absolute_y(void *data) {
    return ((struct wlr_pointer_motion_absolute_event *)data)->y;
}

uint32_t wlr_shim_motion_absolute_time(void *data) {
    return ((struct wlr_pointer_motion_absolute_event *)data)->time_msec;
}

struct wlr_input_device *wlr_shim_motion_absolute_device(void *data) {
    struct wlr_pointer *pointer = ((struct wlr_pointer_motion_absolute_event *)data)->pointer;
    return &pointer->base;
}

uint32_t wlr_shim_button_event_button(void *data) {
    return ((struct wlr_pointer_button_event *)data)->button;
}

uint32_t wlr_shim_button_event_state(void *data) {
    return (uint32_t)((struct wlr_pointer_button_event *)data)->state;
}

uint32_t wlr_shim_button_event_time(void *data) {
    return ((struct wlr_pointer_button_event *)data)->time_msec;
}

uint32_t wlr_shim_key_event_keycode(void *data) {
    return ((struct wlr_keyboard_key_event *)data)->keycode;
}

uint32_t wlr_shim_key_event_state(void *data) {
    return (uint32_t)((struct wlr_keyboard_key_event *)data)->state;
}

uint32_t wlr_shim_key_event_time(void *data) {
    return ((struct wlr_keyboard_key_event *)data)->time_msec;
}

void wlr_shim_seat_notify_modifiers(struct wlr_seat *seat,
        uint32_t depressed, uint32_t latched, uint32_t locked, uint32_t group) {
    struct wlr_keyboard_modifiers mods = {
        .depressed = depressed,
        .latched = latched,
        .locked = locked,
        .group = group,
    };
    wlr_seat_keyboard_notify_modifiers(seat, &mods);
}
