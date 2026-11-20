#define _GNU_SOURCE
#include "gl_api.h"

#include <stdio.h>
#include <string.h>

#if defined(_WIN32)
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
static HMODULE g_opengl;
typedef PROC (WINAPI *MimeowWglGetProcAddress)(LPCSTR);
static MimeowWglGetProcAddress g_wgl_get_proc_address;
#else
#include <dlfcn.h>
static void *g_opengl;
typedef void *(*MimeowGlxGetProcAddress)(const GLubyte *);
static MimeowGlxGetProcAddress g_glx_get_proc_address;
#endif

#define MIMEOW_GL_DEFINE(name, result, args) mimeow_gl_##name##_proc mimeow_gl_##name;
MIMEOW_GL_FUNCTIONS(MIMEOW_GL_DEFINE)
#undef MIMEOW_GL_DEFINE

static int g_loaded;
static int g_gl3;
static char g_error[192];

static void set_error(const char *prefix, const char *name) {
    (void)snprintf(g_error, sizeof(g_error), "%s%s%s",
                   prefix == NULL ? "" : prefix,
                   name == NULL || name[0] == '\0' ? "" : ": ",
                   name == NULL ? "" : name);
}

static void *resolve_symbol(const char *name) {
#if defined(_WIN32)
    PROC address = NULL;
    if (g_wgl_get_proc_address != NULL) {
        address = g_wgl_get_proc_address(name);
        if (address == (PROC)(intptr_t)1 || address == (PROC)(intptr_t)2
                || address == (PROC)(intptr_t)3 || address == (PROC)(intptr_t)-1) {
            address = NULL;
        }
    }
    if (address == NULL && g_opengl != NULL) {
        address = GetProcAddress(g_opengl, name);
    }
    {
        union { PROC function; void *pointer; } value;
        value.function = address;
        return value.pointer;
    }
#else
    void *address = NULL;
    if (g_glx_get_proc_address != NULL) {
        address = g_glx_get_proc_address((const GLubyte *)name);
    }
    if (address == NULL && g_opengl != NULL) {
        address = dlsym(g_opengl, name);
    }
    return address;
#endif
}

#define LOAD_GL(name, required) do { \
    void *address_ = resolve_symbol("gl" #name); \
    memcpy(&mimeow_gl_##name, &address_, sizeof(address_)); \
    if ((required) && mimeow_gl_##name == NULL) { \
        set_error("OpenGL function is unavailable", "gl" #name); \
        goto failure; \
    } \
} while (0)

int mimeow_gl_load(void) {
    if (g_loaded) return 1;
    g_error[0] = '\0';
#if defined(_WIN32)
    g_opengl = LoadLibraryA("opengl32.dll");
    if (g_opengl == NULL) {
        set_error("could not load opengl32.dll", NULL);
        return 0;
    }
    {
        FARPROC address = GetProcAddress(g_opengl, "wglGetProcAddress");
        memcpy(&g_wgl_get_proc_address, &address, sizeof(address));
    }
#else
    g_opengl = dlopen("libGL.so.1", RTLD_LAZY | RTLD_LOCAL);
    if (g_opengl == NULL) {
        g_opengl = dlopen("libOpenGL.so.0", RTLD_LAZY | RTLD_LOCAL);
    }
    if (g_opengl == NULL) {
        set_error("could not load libGL.so.1", NULL);
        return 0;
    }
    {
        void *address = dlsym(g_opengl, "glXGetProcAddressARB");
        if (address == NULL) address = dlsym(g_opengl, "glXGetProcAddress");
        memcpy(&g_glx_get_proc_address, &address, sizeof(address));
    }
#endif

    LOAD_GL(ActiveTexture, 1);
    LOAD_GL(AttachShader, 1);
    LOAD_GL(BindAttribLocation, 1);
    LOAD_GL(BindBuffer, 1);
    LOAD_GL(BindTexture, 1);
    LOAD_GL(BlendEquationSeparate, 1);
    LOAD_GL(BlendFuncSeparate, 1);
    LOAD_GL(BufferData, 1);
    LOAD_GL(Clear, 1);
    LOAD_GL(ClearStencil, 1);
    LOAD_GL(ColorMask, 1);
    LOAD_GL(CompileShader, 1);
    LOAD_GL(CreateProgram, 1);
    LOAD_GL(CreateShader, 1);
    LOAD_GL(CullFace, 1);
    LOAD_GL(DeleteBuffers, 1);
    LOAD_GL(DeleteProgram, 1);
    LOAD_GL(DeleteShader, 1);
    LOAD_GL(DeleteTextures, 1);
    LOAD_GL(DepthMask, 1);
    LOAD_GL(Disable, 1);
    LOAD_GL(DisableVertexAttribArray, 1);
    LOAD_GL(DrawArrays, 1);
    LOAD_GL(Enable, 1);
    LOAD_GL(EnableVertexAttribArray, 1);
    LOAD_GL(Finish, 1);
    LOAD_GL(FrontFace, 1);
    LOAD_GL(GenBuffers, 1);
    LOAD_GL(GenTextures, 1);
    LOAD_GL(GetBooleanv, 1);
    LOAD_GL(GetError, 1);
    LOAD_GL(GetIntegerv, 1);
    LOAD_GL(GetProgramInfoLog, 1);
    LOAD_GL(GetProgramiv, 1);
    LOAD_GL(GetShaderInfoLog, 1);
    LOAD_GL(GetShaderiv, 1);
    LOAD_GL(GetString, 1);
    LOAD_GL(GetUniformLocation, 1);
    LOAD_GL(IsEnabled, 1);
    LOAD_GL(LinkProgram, 1);
    LOAD_GL(PixelStorei, 1);
    LOAD_GL(Scissor, 1);
    LOAD_GL(ShaderSource, 1);
    LOAD_GL(StencilFunc, 1);
    LOAD_GL(StencilMask, 1);
    LOAD_GL(StencilOp, 1);
    LOAD_GL(StencilOpSeparate, 1);
    LOAD_GL(TexImage2D, 1);
    LOAD_GL(TexParameteri, 1);
    LOAD_GL(TexSubImage2D, 1);
    LOAD_GL(Uniform1i, 1);
    LOAD_GL(Uniform2fv, 1);
    LOAD_GL(Uniform4fv, 1);
    LOAD_GL(UseProgram, 1);
    LOAD_GL(VertexAttribPointer, 1);
    LOAD_GL(Viewport, 1);

    LOAD_GL(BindBufferRange, 0);
    LOAD_GL(BindVertexArray, 0);
    LOAD_GL(DeleteVertexArrays, 0);
    LOAD_GL(GenVertexArrays, 0);
    LOAD_GL(GenerateMipmap, 0);
    LOAD_GL(GetUniformBlockIndex, 0);
    LOAD_GL(UniformBlockBinding, 0);
    {
        const char *version = (const char *)mimeow_gl_GetString(GL_VERSION);
        int major = 0;
        int minor = 0;
        if (version != NULL) (void)sscanf(version, "%d.%d", &major, &minor);
        g_gl3 = (major > 3 || (major == 3 && minor >= 2))
            && mimeow_gl_BindBufferRange != NULL
            && mimeow_gl_BindVertexArray != NULL
            && mimeow_gl_DeleteVertexArrays != NULL
            && mimeow_gl_GenVertexArrays != NULL
            && mimeow_gl_GenerateMipmap != NULL
            && mimeow_gl_GetUniformBlockIndex != NULL
            && mimeow_gl_UniformBlockBinding != NULL;
    }
    g_loaded = 1;
    return 1;

failure:
    mimeow_gl_unload();
    return 0;
}

void mimeow_gl_unload(void) {
#define MIMEOW_GL_CLEAR(name, result, args) mimeow_gl_##name = NULL;
    MIMEOW_GL_FUNCTIONS(MIMEOW_GL_CLEAR)
#undef MIMEOW_GL_CLEAR
#if defined(_WIN32)
    g_wgl_get_proc_address = NULL;
    if (g_opengl != NULL) FreeLibrary(g_opengl);
#else
    g_glx_get_proc_address = NULL;
    if (g_opengl != NULL) dlclose(g_opengl);
#endif
    g_opengl = NULL;
    g_loaded = 0;
    g_gl3 = 0;
}

int mimeow_gl_gl3_available(void) {
    return g_loaded && g_gl3;
}

const char *mimeow_gl_error(void) {
    return g_error;
}

#undef LOAD_GL
