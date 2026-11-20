#ifndef MIMEOW_NOTIFICATION_RENDERER_H
#define MIMEOW_NOTIFICATION_RENDERER_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define MIMEOW_NOTIFICATION_EXPORT __declspec(dllexport)
#else
#define MIMEOW_NOTIFICATION_EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MimeowNotificationFrame {
    int32_t width;
    int32_t height;
    uint32_t delta_seconds_bits;
    uint32_t accent_rgb;
    uint32_t scale_bits;
    int32_t animation_mode;
} MimeowNotificationFrame;

MIMEOW_NOTIFICATION_EXPORT int mimeow_vlkr_notifications_init(
        const unsigned char *medium_font,
        int medium_font_size,
        const unsigned char *bold_font,
        int bold_font_size);
MIMEOW_NOTIFICATION_EXPORT int mimeow_vlkr_notifications_enqueue(
        const char *message, int type);
MIMEOW_NOTIFICATION_EXPORT int mimeow_vlkr_notifications_enqueue_ex(
        const char *message, const char *badge, int type);
MIMEOW_NOTIFICATION_EXPORT void mimeow_vlkr_notifications_clear(void);
MIMEOW_NOTIFICATION_EXPORT int mimeow_vlkr_notifications_render(
        const MimeowNotificationFrame *frame);
MIMEOW_NOTIFICATION_EXPORT void mimeow_vlkr_notifications_shutdown(void);
MIMEOW_NOTIFICATION_EXPORT int mimeow_vlkr_notifications_backend(void);
MIMEOW_NOTIFICATION_EXPORT const char *mimeow_vlkr_notifications_error(void);

#ifdef MIMEOW_NOTIFICATION_TESTING
size_t mimeow_notifications_test_count(void);
const char *mimeow_notifications_test_message(size_t index);
int mimeow_notifications_test_type(size_t index);
float mimeow_notifications_test_age(size_t index);
void mimeow_notifications_test_set_age(size_t index, float age);
void mimeow_notifications_test_expire(void);
float mimeow_notifications_test_draw_step(size_t index, float delta_seconds);
float mimeow_notifications_test_animation_progress(float age);
#endif

#ifdef __cplusplus
}
#endif

#endif
