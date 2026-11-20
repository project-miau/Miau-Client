#include "notification_renderer.h"
#include "gl_api.h"

#include "third_party/nanovg/nanovg.h"
#define NANOVG_GL2 1
#define NANOVG_GL3 1
#include "third_party/nanovg/nanovg_gl.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

enum {
    NOTIFICATION_CAPACITY = 10,
    NOTIFICATION_MESSAGE_CAPACITY = 101,
    NOTIFICATION_BADGE_CAPACITY = 32
};

static const float NOTIFICATION_LIFETIME = 5.0f;
static const float NOTIFICATION_FADE_TIME = 0.2f;
static const float NOTIFICATION_ANIMATION_TIME = 0.25f;
static const char NOTIFICATION_BRAND[] = "Miau";

typedef struct MimeowColor {
    float r;
    float g;
    float b;
    float a;
} MimeowColor;

typedef struct MimeowRect {
    float x;
    float y;
    float w;
    float h;
} MimeowRect;

typedef struct MimeowNotification {
    char message[NOTIFICATION_MESSAGE_CAPACITY];
    char badge[NOTIFICATION_BADGE_CAPACITY];
    int type;
    float age;
} MimeowNotification;

typedef struct MimeowGlState {
    GLint viewport[4];
    GLint scissor_box[4];
    GLint program;
    GLint active_texture;
    GLint texture_2d;
    GLint array_buffer;
    GLint element_array_buffer;
    GLint vertex_array;
    GLint uniform_buffer;
    GLint blend_src_rgb;
    GLint blend_dst_rgb;
    GLint blend_src_alpha;
    GLint blend_dst_alpha;
    GLint blend_eq_rgb;
    GLint blend_eq_alpha;
    GLint cull_face;
    GLint front_face;
    GLint stencil_func;
    GLint stencil_ref;
    GLint stencil_value_mask;
    GLint stencil_write_mask;
    GLint stencil_fail;
    GLint stencil_zfail;
    GLint stencil_zpass;
    GLboolean blend;
    GLboolean cull;
    GLboolean depth;
    GLboolean scissor;
    GLboolean stencil;
    GLboolean color_mask[4];
    GLboolean depth_mask;
    int gl3;
} MimeowGlState;

static const MimeowColor SURFACE = {0.0784314f, 0.0784314f, 0.0784314f, 1.0f};
static const MimeowColor TEXT = {0.8666667f, 0.8666667f, 0.8666667f, 1.0f};
static const MimeowColor SUCCESS = {0.5490196f, 0.9254902f, 0.1686275f, 1.0f};
static const MimeowColor UNLOAD = {0.8666667f, 0.2784314f, 0.2784314f, 1.0f};
static const MimeowColor ANTICHEAT = {0.6392157f, 0.2980392f, 1.0f, 1.0f};
static const MimeowColor DEFAULT_ACCENT = {0.7294118f, 0.5607843f, 0.4745098f, 1.0f};

static NVGcontext *g_vg;
static int g_gl2_backend;
static int g_medium_font = -1;
static int g_bold_font = -1;
static MimeowColor g_accent = {0.7294118f, 0.5607843f, 0.4745098f, 1.0f};
static float g_text_optical_correction = 1.0f;
static MimeowNotification g_notifications[NOTIFICATION_CAPACITY];
static size_t g_notification_count;
static char g_error[256];

static void set_error(const char *message) {
    if (message == NULL) message = "unknown renderer error";
    (void)snprintf(g_error, sizeof(g_error), "%s", message);
}

static float clampf(float value, float low, float high) {
    if (value < low) return low;
    if (value > high) return high;
    return value;
}

static float smoothstepf(float value) {
    return (3.0f - 2.0f * value) * value * value;
}

static MimeowColor with_alpha(MimeowColor color, float alpha) {
    color.a = alpha;
    return color;
}

static NVGcolor nvg_color(MimeowColor color) {
    return nvgRGBAf(color.r, color.g, color.b, color.a);
}

static void save_gl_state(MimeowGlState *state, int gl3) {
    memset(state, 0, sizeof(*state));
    state->gl3 = gl3;
    glGetIntegerv(GL_VIEWPORT, state->viewport);
    glGetIntegerv(GL_SCISSOR_BOX, state->scissor_box);
    glGetIntegerv(GL_CURRENT_PROGRAM, &state->program);
    glGetIntegerv(GL_ACTIVE_TEXTURE, &state->active_texture);
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &state->texture_2d);
    glGetIntegerv(GL_ARRAY_BUFFER_BINDING, &state->array_buffer);
    glGetIntegerv(GL_ELEMENT_ARRAY_BUFFER_BINDING, &state->element_array_buffer);
    if (gl3) {
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &state->vertex_array);
        glGetIntegerv(GL_UNIFORM_BUFFER_BINDING, &state->uniform_buffer);
    }
    glGetIntegerv(GL_BLEND_SRC_RGB, &state->blend_src_rgb);
    glGetIntegerv(GL_BLEND_DST_RGB, &state->blend_dst_rgb);
    glGetIntegerv(GL_BLEND_SRC_ALPHA, &state->blend_src_alpha);
    glGetIntegerv(GL_BLEND_DST_ALPHA, &state->blend_dst_alpha);
    glGetIntegerv(GL_BLEND_EQUATION_RGB, &state->blend_eq_rgb);
    glGetIntegerv(GL_BLEND_EQUATION_ALPHA, &state->blend_eq_alpha);
    glGetIntegerv(GL_CULL_FACE_MODE, &state->cull_face);
    glGetIntegerv(GL_FRONT_FACE, &state->front_face);
    glGetIntegerv(GL_STENCIL_FUNC, &state->stencil_func);
    glGetIntegerv(GL_STENCIL_REF, &state->stencil_ref);
    glGetIntegerv(GL_STENCIL_VALUE_MASK, &state->stencil_value_mask);
    glGetIntegerv(GL_STENCIL_WRITEMASK, &state->stencil_write_mask);
    glGetIntegerv(GL_STENCIL_FAIL, &state->stencil_fail);
    glGetIntegerv(GL_STENCIL_PASS_DEPTH_FAIL, &state->stencil_zfail);
    glGetIntegerv(GL_STENCIL_PASS_DEPTH_PASS, &state->stencil_zpass);
    state->blend = glIsEnabled(GL_BLEND);
    state->cull = glIsEnabled(GL_CULL_FACE);
    state->depth = glIsEnabled(GL_DEPTH_TEST);
    state->scissor = glIsEnabled(GL_SCISSOR_TEST);
    state->stencil = glIsEnabled(GL_STENCIL_TEST);
    glGetBooleanv(GL_COLOR_WRITEMASK, state->color_mask);
    glGetBooleanv(GL_DEPTH_WRITEMASK, &state->depth_mask);
}

static void restore_cap(GLenum cap, GLboolean enabled) {
    if (enabled) glEnable(cap);
    else glDisable(cap);
}

static void restore_gl_state(const MimeowGlState *state) {
    glUseProgram((GLuint)state->program);
    glActiveTexture((GLenum)state->active_texture);
    glBindTexture(GL_TEXTURE_2D, (GLuint)state->texture_2d);
    if (state->gl3) {
        glBindVertexArray((GLuint)state->vertex_array);
    }
    glBindBuffer(GL_ARRAY_BUFFER, (GLuint)state->array_buffer);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, (GLuint)state->element_array_buffer);
    if (state->gl3) {
        glBindBuffer(GL_UNIFORM_BUFFER, (GLuint)state->uniform_buffer);
    }
    glBlendFuncSeparate((GLenum)state->blend_src_rgb,
                        (GLenum)state->blend_dst_rgb,
                        (GLenum)state->blend_src_alpha,
                        (GLenum)state->blend_dst_alpha);
    glBlendEquationSeparate((GLenum)state->blend_eq_rgb,
                            (GLenum)state->blend_eq_alpha);
    glCullFace((GLenum)state->cull_face);
    glFrontFace((GLenum)state->front_face);
    glStencilFunc((GLenum)state->stencil_func,
                  state->stencil_ref,
                  (GLuint)state->stencil_value_mask);
    glStencilMask((GLuint)state->stencil_write_mask);
    glStencilOp((GLenum)state->stencil_fail,
                (GLenum)state->stencil_zfail,
                (GLenum)state->stencil_zpass);
    glColorMask(state->color_mask[0], state->color_mask[1],
                state->color_mask[2], state->color_mask[3]);
    glDepthMask(state->depth_mask);
    glViewport(state->viewport[0], state->viewport[1],
               state->viewport[2], state->viewport[3]);
    glScissor(state->scissor_box[0], state->scissor_box[1],
              state->scissor_box[2], state->scissor_box[3]);
    restore_cap(GL_BLEND, state->blend);
    restore_cap(GL_CULL_FACE, state->cull);
    restore_cap(GL_DEPTH_TEST, state->depth);
    restore_cap(GL_SCISSOR_TEST, state->scissor);
    restore_cap(GL_STENCIL_TEST, state->stencil);
}

static void clear_frame_stencil(void) {
    glDisable(GL_SCISSOR_TEST);
    glStencilMask(0xffffffffu);
    glClearStencil(0);
    glClear(GL_STENCIL_BUFFER_BIT);
}

static void rounded_rect(MimeowRect rect, float radius, MimeowColor color) {
    nvgBeginPath(g_vg);
    nvgRoundedRect(g_vg, rect.x, rect.y,
                   fmaxf(0.0f, rect.w), fmaxf(0.0f, rect.h), radius);
    nvgFillColor(g_vg, nvg_color(color));
    nvgFill(g_vg);
}

static float text_width(const char *value, float size, int bold) {
    float bounds[4] = {0, 0, 0, 0};
    nvgFontFaceId(g_vg, bold ? g_bold_font : g_medium_font);
    nvgFontSize(g_vg, size);
    nvgTextBounds(g_vg, 0.0f, 0.0f, value, NULL, bounds);
    return bounds[2] - bounds[0];
}

static void draw_text(const char *value,
                      float x,
                      float y,
                      float size,
                      MimeowColor color,
                      int align,
                      int bold) {
    nvgFontFaceId(g_vg, bold ? g_bold_font : g_medium_font);
    nvgFontSize(g_vg, size);
    nvgTextAlign(g_vg, align | NVG_ALIGN_MIDDLE);
    nvgFillColor(g_vg, nvg_color(color));
    nvgText(g_vg, x, y + g_text_optical_correction, value, NULL);
}

static void expire_notifications(void) {
    while (g_notification_count > 0U
            && g_notifications[0].age > NOTIFICATION_LIFETIME) {
        if (g_notification_count > 1U) {
            memmove(&g_notifications[0], &g_notifications[1],
                    (g_notification_count - 1U) * sizeof(g_notifications[0]));
        }
        --g_notification_count;
    }
}

static float notification_alpha(float age) {
    if (age < NOTIFICATION_FADE_TIME) {
        return smoothstepf(age / NOTIFICATION_FADE_TIME);
    }
    if (age > NOTIFICATION_LIFETIME - NOTIFICATION_FADE_TIME) {
        return smoothstepf((NOTIFICATION_LIFETIME - age) /
                           NOTIFICATION_FADE_TIME);
    }
    return 1.0f;
}

static float notification_animation_progress(float age) {
    float progress;
    if (age < NOTIFICATION_ANIMATION_TIME) {
        progress = clampf(age / NOTIFICATION_ANIMATION_TIME, 0.0f, 1.0f);
        return 1.0f - (progress - 1.0f) * (progress - 1.0f);
    }
    if (age > NOTIFICATION_LIFETIME - NOTIFICATION_ANIMATION_TIME) {
        progress = clampf(
                (age - (NOTIFICATION_LIFETIME - NOTIFICATION_ANIMATION_TIME))
                        / NOTIFICATION_ANIMATION_TIME,
                0.0f,
                1.0f);
        return (1.0f - progress) * (1.0f - progress);
    }
    return 1.0f;
}

static void draw_notifications(float delta_seconds, float scale, int animation_mode) {
    float stack = 0.0f;
    size_t index;
    for (index = 0U; index < g_notification_count; ++index) {
        MimeowNotification *notification = &g_notifications[index];
        MimeowColor status;
        float progress;
        float alpha;
        float x;
        float y;
        float width;
        float badge_x;
        float badge_width;
        float message_x;
        const float s = scale;

        notification->age += delta_seconds;
        progress = animation_mode == 2
                ? notification_animation_progress(notification->age) : 1.0f;
        alpha = animation_mode == 1
                ? notification_alpha(notification->age) : progress;
        status = notification->type == 0
                ? SUCCESS
                : (notification->type == 2
                   ? UNLOAD
                   : (notification->type == 3 ? ANTICHEAT : g_accent));
        y = 12.0f * s + stack;
        badge_width = text_width(notification->badge, 14.0f * s, 1)
                + 20.0f * s;
        width = 8.0f * s + badge_width + 8.0f * s
                + text_width(notification->message, 14.0f * s, 0)
                + 16.0f * s;
        x = 12.0f * s;
        if (animation_mode == 1) {
            x -= (1.0f - alpha) * 100.0f * s;
        }
        badge_x = x + 8.0f * s;
        message_x = badge_x + badge_width + 8.0f * s;

        nvgSave(g_vg);
        nvgGlobalAlpha(g_vg, alpha);
        if (animation_mode == 2) {
            nvgTranslate(g_vg, x + width * 0.5f, y + 21.0f * s);
            nvgScale(g_vg, progress, progress);
            nvgTranslate(g_vg, -(x + width * 0.5f), -(y + 21.0f * s));
        }
        rounded_rect((MimeowRect){x, y, width, 42.0f * s},
                     20.0f * s, SURFACE);
        rounded_rect((MimeowRect){badge_x, y + 8.0f * s,
                                  badge_width, 26.0f * s},
                     10.0f * s, with_alpha(status, 0.15f));
        draw_text(notification->badge, badge_x + badge_width * 0.5f,
                  y + 21.0f * s,
                  14.0f * s, status, NVG_ALIGN_CENTER, 1);
        draw_text(notification->message, message_x, y + 21.0f * s,
                  14.0f * s, TEXT, NVG_ALIGN_LEFT, 0);
        nvgRestore(g_vg);
        stack += 50.0f * s * alpha;
    }
}

static unsigned char *copy_font(const unsigned char *source, int size) {
    unsigned char *copy;
    if (source == NULL || size <= 0) return NULL;
    copy = (unsigned char *)malloc((size_t)size);
    if (copy != NULL) memcpy(copy, source, (size_t)size);
    return copy;
}

static void delete_context(NVGcontext *context) {
    if (context == NULL) return;
    if (g_gl2_backend) nvgDeleteGL2(context);
    else nvgDeleteGL3(context);
    g_gl2_backend = 0;
}

int mimeow_vlkr_notifications_init(const unsigned char *medium_font,
                                    int medium_font_size,
                                    const unsigned char *bold_font,
                                    int bold_font_size) {
    MimeowGlState state;
    unsigned char *medium_copy;
    unsigned char *bold_copy;
    int gl3;

    g_error[0] = '\0';
    if (g_vg != NULL) return 1;
    if (!mimeow_gl_load()) {
        set_error(mimeow_gl_error());
        return 0;
    }

    gl3 = mimeow_gl_gl3_available();
    save_gl_state(&state, gl3);
    g_gl2_backend = 0;
    g_vg = gl3 ? nvgCreateGL3(NVG_ANTIALIAS | NVG_STENCIL_STROKES) : NULL;
    if (g_vg == NULL) {
        g_vg = nvgCreateGL2(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        g_gl2_backend = g_vg != NULL ? 1 : 0;
    }
    if (g_vg == NULL) {
        restore_gl_state(&state);
        mimeow_gl_unload();
        set_error("nvgCreateGL3/GL2 failed (the current OpenGL context is not capable)");
        return 0;
    }

    medium_copy = copy_font(medium_font, medium_font_size);
    bold_copy = copy_font(bold_font, bold_font_size);
    if (medium_copy == NULL || bold_copy == NULL) {
        free(medium_copy);
        free(bold_copy);
        delete_context(g_vg);
        g_vg = NULL;
        restore_gl_state(&state);
        mimeow_gl_unload();
        set_error("could not copy the bundled Inter fonts");
        return 0;
    }

    g_medium_font = nvgCreateFontMem(g_vg, "Valkyrie Inter Medium",
                                     medium_copy, medium_font_size, 1);
    g_bold_font = nvgCreateFontMem(g_vg, "Valkyrie Inter Bold",
                                   bold_copy, bold_font_size, 1);
    if (g_medium_font < 0 || g_bold_font < 0) {
        delete_context(g_vg);
        g_vg = NULL;
        g_medium_font = -1;
        g_bold_font = -1;
        restore_gl_state(&state);
        mimeow_gl_unload();
        set_error("NanoVG could not build the Inter font atlas");
        return 0;
    }

    g_accent = DEFAULT_ACCENT;
    g_text_optical_correction = 1.0f;
    restore_gl_state(&state);
    return 1;
}

int mimeow_vlkr_notifications_enqueue(const char *message, int type) {
    return mimeow_vlkr_notifications_enqueue_ex(message, NOTIFICATION_BRAND, type);
}

int mimeow_vlkr_notifications_enqueue_ex(const char *message,
                                         const char *badge,
                                         int type) {
    MimeowNotification *notification;
    if (message == NULL) return 0;
    if (g_notification_count == NOTIFICATION_CAPACITY) {
        memmove(&g_notifications[0], &g_notifications[1],
                (NOTIFICATION_CAPACITY - 1U) * sizeof(g_notifications[0]));
        --g_notification_count;
    }
    notification = &g_notifications[g_notification_count++];
    (void)snprintf(notification->message, sizeof(notification->message), "%s", message);
    if (badge == NULL || badge[0] == '\0') {
        badge = NOTIFICATION_BRAND;
    }
    (void)snprintf(notification->badge, sizeof(notification->badge), "%s", badge);
    notification->type = type;
    notification->age = 0.0f;
    return 1;
}

void mimeow_vlkr_notifications_clear(void) {
    g_notification_count = 0U;
}

int mimeow_vlkr_notifications_render(const MimeowNotificationFrame *frame) {
    MimeowGlState state;
    float delta_seconds;
    float scale;
    int animation_mode;
    if (g_vg == NULL || frame == NULL || frame->width <= 0 || frame->height <= 0) {
        set_error("renderer is not initialized");
        return 0;
    }

    memcpy(&delta_seconds, &frame->delta_seconds_bits, sizeof(delta_seconds));
    memcpy(&scale, &frame->scale_bits, sizeof(scale));
    delta_seconds = clampf(delta_seconds, 0.0f, 0.25f);
    if (scale != 0.75f && scale != 1.0f && scale != 1.5f && scale != 2.0f) {
        scale = 1.0f;
    }
    animation_mode = frame->animation_mode;
    if (animation_mode < 0 || animation_mode > 2) {
        animation_mode = 2;
    }
    g_accent.r = (float)((frame->accent_rgb >> 16U) & 0xffU) / 255.0f;
    g_accent.g = (float)((frame->accent_rgb >> 8U) & 0xffU) / 255.0f;
    g_accent.b = (float)(frame->accent_rgb & 0xffU) / 255.0f;
    g_accent.a = 1.0f;

    expire_notifications();
    g_text_optical_correction = 1.0f;
    save_gl_state(&state, !g_gl2_backend);
    glViewport(0, 0, frame->width, frame->height);
    clear_frame_stencil();
    nvgBeginFrame(g_vg, (float)frame->width, (float)frame->height, 1.0f);
    draw_notifications(delta_seconds, scale, animation_mode);
    nvgEndFrame(g_vg);
    restore_gl_state(&state);
    return 1;
}

void mimeow_vlkr_notifications_shutdown(void) {
    MimeowGlState state;
    g_notification_count = 0U;
    g_accent = DEFAULT_ACCENT;
    g_text_optical_correction = 1.0f;
    if (g_vg == NULL) {
        mimeow_gl_unload();
        return;
    }
    save_gl_state(&state, !g_gl2_backend);
    delete_context(g_vg);
    g_vg = NULL;
    g_medium_font = -1;
    g_bold_font = -1;
    restore_gl_state(&state);
    mimeow_gl_unload();
}

int mimeow_vlkr_notifications_backend(void) {
    if (g_vg == NULL) return 0;
    return g_gl2_backend ? 2 : 3;
}

const char *mimeow_vlkr_notifications_error(void) {
    return g_error[0] == '\0' ? "" : g_error;
}

#ifdef MIMEOW_NOTIFICATION_TESTING
size_t mimeow_notifications_test_count(void) {
    return g_notification_count;
}

const char *mimeow_notifications_test_message(size_t index) {
    return index < g_notification_count ? g_notifications[index].message : NULL;
}

int mimeow_notifications_test_type(size_t index) {
    return index < g_notification_count ? g_notifications[index].type : 0;
}

float mimeow_notifications_test_age(size_t index) {
    return index < g_notification_count ? g_notifications[index].age : -1.0f;
}

void mimeow_notifications_test_set_age(size_t index, float age) {
    if (index < g_notification_count) g_notifications[index].age = age;
}

void mimeow_notifications_test_expire(void) {
    expire_notifications();
}

float mimeow_notifications_test_draw_step(size_t index, float delta_seconds) {
    if (index >= g_notification_count) return -1.0f;
    g_notifications[index].age += delta_seconds;
    return notification_alpha(g_notifications[index].age);
}

float mimeow_notifications_test_animation_progress(float age) {
    return notification_animation_progress(age);
}
#endif
