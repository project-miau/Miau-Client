#ifndef MIMEOW_NOTIFICATIONS_GL_API_H
#define MIMEOW_NOTIFICATIONS_GL_API_H

/* A deliberately small OpenGL ABI surface. Function addresses are resolved
 * from the live Minecraft context, so the notification DLL does not link to
 * a platform-specific GL import table (especially important on Windows). */

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define MIMEOW_GL_APIENTRY __stdcall
#else
#define MIMEOW_GL_APIENTRY
#endif

typedef unsigned char GLboolean;
typedef unsigned char GLubyte;
typedef unsigned int GLenum;
typedef unsigned int GLbitfield;
typedef unsigned int GLuint;
typedef int GLint;
typedef int GLsizei;
typedef char GLchar;
typedef float GLfloat;
typedef double GLdouble;
typedef void GLvoid;
typedef intptr_t GLintptr;
typedef intptr_t GLsizeiptr;

#define GL_FALSE 0
#define GL_TRUE 1
#define GL_NO_ERROR 0
#define GL_INVALID_ENUM 0x0500
#define GL_ZERO 0
#define GL_ONE 1
#define GL_SRC_COLOR 0x0300
#define GL_ONE_MINUS_SRC_COLOR 0x0301
#define GL_SRC_ALPHA 0x0302
#define GL_ONE_MINUS_SRC_ALPHA 0x0303
#define GL_DST_ALPHA 0x0304
#define GL_ONE_MINUS_DST_ALPHA 0x0305
#define GL_DST_COLOR 0x0306
#define GL_ONE_MINUS_DST_COLOR 0x0307
#define GL_SRC_ALPHA_SATURATE 0x0308
#define GL_FRONT 0x0404
#define GL_BACK 0x0405
#define GL_TRIANGLES 0x0004
#define GL_TRIANGLE_STRIP 0x0005
#define GL_TRIANGLE_FAN 0x0006
#define GL_NEVER 0x0200
#define GL_LESS 0x0201
#define GL_EQUAL 0x0202
#define GL_LEQUAL 0x0203
#define GL_GREATER 0x0204
#define GL_NOTEQUAL 0x0205
#define GL_GEQUAL 0x0206
#define GL_ALWAYS 0x0207
#define GL_KEEP 0x1E00
#define GL_INCR 0x1E02
#define GL_DECR 0x1E03
#define GL_INCR_WRAP 0x8507
#define GL_DECR_WRAP 0x8508
#define GL_FLOAT 0x1406
#define GL_UNSIGNED_BYTE 0x1401
#define GL_RGBA 0x1908
#define GL_LUMINANCE 0x1909
#define GL_RED 0x1903
#define GL_R8 0x8229
#define GL_TEXTURE_2D 0x0DE1
#define GL_TEXTURE0 0x84C0
#define GL_TEXTURE_BINDING_2D 0x8069
#define GL_TEXTURE_MIN_FILTER 0x2801
#define GL_TEXTURE_MAG_FILTER 0x2800
#define GL_TEXTURE_WRAP_S 0x2802
#define GL_TEXTURE_WRAP_T 0x2803
#define GL_REPEAT 0x2901
#define GL_CLAMP_TO_EDGE 0x812F
#define GL_LINEAR 0x2601
#define GL_NEAREST 0x2600
#define GL_LINEAR_MIPMAP_LINEAR 0x2703
#define GL_NEAREST_MIPMAP_NEAREST 0x2700
#define GL_GENERATE_MIPMAP 0x8191
#define GL_ARRAY_BUFFER 0x8892
#define GL_ARRAY_BUFFER_BINDING 0x8894
#define GL_ELEMENT_ARRAY_BUFFER 0x8893
#define GL_ELEMENT_ARRAY_BUFFER_BINDING 0x8895
#define GL_STREAM_DRAW 0x88E0
#define GL_VERTEX_ARRAY_BINDING 0x85B5
#define GL_UNIFORM_BUFFER 0x8A11
#define GL_UNIFORM_BUFFER_BINDING 0x8A28
#define GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT 0x8A34
#define GL_VERTEX_SHADER 0x8B31
#define GL_FRAGMENT_SHADER 0x8B30
#define GL_COMPILE_STATUS 0x8B81
#define GL_LINK_STATUS 0x8B82
#define GL_CURRENT_PROGRAM 0x8B8D
#define GL_VERSION 0x1F02
#define GL_ACTIVE_TEXTURE 0x84E0
#define GL_BLEND 0x0BE2
#define GL_BLEND_SRC_RGB 0x80C9
#define GL_BLEND_DST_RGB 0x80C8
#define GL_BLEND_SRC_ALPHA 0x80CB
#define GL_BLEND_DST_ALPHA 0x80CA
#define GL_BLEND_EQUATION_RGB 0x8009
#define GL_BLEND_EQUATION_ALPHA 0x883D
#define GL_CULL_FACE 0x0B44
#define GL_CULL_FACE_MODE 0x0B45
#define GL_FRONT_FACE 0x0B46
#define GL_DEPTH_TEST 0x0B71
#define GL_DEPTH_WRITEMASK 0x0B72
#define GL_SCISSOR_TEST 0x0C11
#define GL_SCISSOR_BOX 0x0C10
#define GL_STENCIL_TEST 0x0B90
#define GL_STENCIL_FUNC 0x0B92
#define GL_STENCIL_VALUE_MASK 0x0B93
#define GL_STENCIL_FAIL 0x0B94
#define GL_STENCIL_PASS_DEPTH_FAIL 0x0B95
#define GL_STENCIL_PASS_DEPTH_PASS 0x0B96
#define GL_STENCIL_REF 0x0B97
#define GL_STENCIL_WRITEMASK 0x0B98
#define GL_COLOR_WRITEMASK 0x0C23
#define GL_STENCIL_BUFFER_BIT 0x00000400
#define GL_VIEWPORT 0x0BA2
#define GL_UNPACK_ROW_LENGTH 0x0CF2
#define GL_UNPACK_SKIP_ROWS 0x0CF3
#define GL_UNPACK_SKIP_PIXELS 0x0CF4
#define GL_UNPACK_ALIGNMENT 0x0CF5
#define GL_CCW 0x0901

#define MIMEOW_GL_FUNCTIONS(X) \
    X(ActiveTexture, void, (GLenum texture)) \
    X(AttachShader, void, (GLuint program, GLuint shader)) \
    X(BindAttribLocation, void, (GLuint program, GLuint index, const GLchar *name)) \
    X(BindBuffer, void, (GLenum target, GLuint buffer)) \
    X(BindBufferRange, void, (GLenum target, GLuint index, GLuint buffer, GLintptr offset, GLsizeiptr size)) \
    X(BindTexture, void, (GLenum target, GLuint texture)) \
    X(BindVertexArray, void, (GLuint array)) \
    X(BlendEquationSeparate, void, (GLenum modeRGB, GLenum modeAlpha)) \
    X(BlendFuncSeparate, void, (GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha)) \
    X(BufferData, void, (GLenum target, GLsizeiptr size, const void *data, GLenum usage)) \
    X(Clear, void, (GLbitfield mask)) \
    X(ClearStencil, void, (GLint s)) \
    X(ColorMask, void, (GLboolean red, GLboolean green, GLboolean blue, GLboolean alpha)) \
    X(CompileShader, void, (GLuint shader)) \
    X(CreateProgram, GLuint, (void)) \
    X(CreateShader, GLuint, (GLenum type)) \
    X(CullFace, void, (GLenum mode)) \
    X(DeleteBuffers, void, (GLsizei n, const GLuint *buffers)) \
    X(DeleteProgram, void, (GLuint program)) \
    X(DeleteShader, void, (GLuint shader)) \
    X(DeleteTextures, void, (GLsizei n, const GLuint *textures)) \
    X(DeleteVertexArrays, void, (GLsizei n, const GLuint *arrays)) \
    X(DepthMask, void, (GLboolean flag)) \
    X(Disable, void, (GLenum cap)) \
    X(DisableVertexAttribArray, void, (GLuint index)) \
    X(DrawArrays, void, (GLenum mode, GLint first, GLsizei count)) \
    X(Enable, void, (GLenum cap)) \
    X(EnableVertexAttribArray, void, (GLuint index)) \
    X(Finish, void, (void)) \
    X(FrontFace, void, (GLenum mode)) \
    X(GenBuffers, void, (GLsizei n, GLuint *buffers)) \
    X(GenTextures, void, (GLsizei n, GLuint *textures)) \
    X(GenVertexArrays, void, (GLsizei n, GLuint *arrays)) \
    X(GenerateMipmap, void, (GLenum target)) \
    X(GetBooleanv, void, (GLenum pname, GLboolean *data)) \
    X(GetError, GLenum, (void)) \
    X(GetIntegerv, void, (GLenum pname, GLint *data)) \
    X(GetProgramInfoLog, void, (GLuint program, GLsizei maxLength, GLsizei *length, GLchar *infoLog)) \
    X(GetProgramiv, void, (GLuint program, GLenum pname, GLint *params)) \
    X(GetShaderInfoLog, void, (GLuint shader, GLsizei maxLength, GLsizei *length, GLchar *infoLog)) \
    X(GetShaderiv, void, (GLuint shader, GLenum pname, GLint *params)) \
    X(GetString, const GLubyte *, (GLenum name)) \
    X(GetUniformBlockIndex, GLuint, (GLuint program, const GLchar *uniformBlockName)) \
    X(GetUniformLocation, GLint, (GLuint program, const GLchar *name)) \
    X(IsEnabled, GLboolean, (GLenum cap)) \
    X(LinkProgram, void, (GLuint program)) \
    X(PixelStorei, void, (GLenum pname, GLint param)) \
    X(Scissor, void, (GLint x, GLint y, GLsizei width, GLsizei height)) \
    X(ShaderSource, void, (GLuint shader, GLsizei count, const GLchar *const *string, const GLint *length)) \
    X(StencilFunc, void, (GLenum func, GLint ref, GLuint mask)) \
    X(StencilMask, void, (GLuint mask)) \
    X(StencilOp, void, (GLenum fail, GLenum zfail, GLenum zpass)) \
    X(StencilOpSeparate, void, (GLenum face, GLenum sfail, GLenum dpfail, GLenum dppass)) \
    X(TexImage2D, void, (GLenum target, GLint level, GLint internalformat, GLsizei width, GLsizei height, GLint border, GLenum format, GLenum type, const void *pixels)) \
    X(TexParameteri, void, (GLenum target, GLenum pname, GLint param)) \
    X(TexSubImage2D, void, (GLenum target, GLint level, GLint xoffset, GLint yoffset, GLsizei width, GLsizei height, GLenum format, GLenum type, const void *pixels)) \
    X(Uniform1i, void, (GLint location, GLint v0)) \
    X(Uniform2fv, void, (GLint location, GLsizei count, const GLfloat *value)) \
    X(Uniform4fv, void, (GLint location, GLsizei count, const GLfloat *value)) \
    X(UniformBlockBinding, void, (GLuint program, GLuint uniformBlockIndex, GLuint uniformBlockBinding)) \
    X(UseProgram, void, (GLuint program)) \
    X(VertexAttribPointer, void, (GLuint index, GLint size, GLenum type, GLboolean normalized, GLsizei stride, const void *pointer)) \
    X(Viewport, void, (GLint x, GLint y, GLsizei width, GLsizei height))

#define MIMEOW_GL_DECLARE(name, result, args) \
    typedef result (MIMEOW_GL_APIENTRY *mimeow_gl_##name##_proc) args; \
    extern mimeow_gl_##name##_proc mimeow_gl_##name;
MIMEOW_GL_FUNCTIONS(MIMEOW_GL_DECLARE)
#undef MIMEOW_GL_DECLARE

#define glActiveTexture mimeow_gl_ActiveTexture
#define glAttachShader mimeow_gl_AttachShader
#define glBindAttribLocation mimeow_gl_BindAttribLocation
#define glBindBuffer mimeow_gl_BindBuffer
#define glBindBufferRange mimeow_gl_BindBufferRange
#define glBindTexture mimeow_gl_BindTexture
#define glBindVertexArray mimeow_gl_BindVertexArray
#define glBlendEquationSeparate mimeow_gl_BlendEquationSeparate
#define glBlendFuncSeparate mimeow_gl_BlendFuncSeparate
#define glBufferData mimeow_gl_BufferData
#define glClear mimeow_gl_Clear
#define glClearStencil mimeow_gl_ClearStencil
#define glColorMask mimeow_gl_ColorMask
#define glCompileShader mimeow_gl_CompileShader
#define glCreateProgram mimeow_gl_CreateProgram
#define glCreateShader mimeow_gl_CreateShader
#define glCullFace mimeow_gl_CullFace
#define glDeleteBuffers mimeow_gl_DeleteBuffers
#define glDeleteProgram mimeow_gl_DeleteProgram
#define glDeleteShader mimeow_gl_DeleteShader
#define glDeleteTextures mimeow_gl_DeleteTextures
#define glDeleteVertexArrays mimeow_gl_DeleteVertexArrays
#define glDepthMask mimeow_gl_DepthMask
#define glDisable mimeow_gl_Disable
#define glDisableVertexAttribArray mimeow_gl_DisableVertexAttribArray
#define glDrawArrays mimeow_gl_DrawArrays
#define glEnable mimeow_gl_Enable
#define glEnableVertexAttribArray mimeow_gl_EnableVertexAttribArray
#define glFinish mimeow_gl_Finish
#define glFrontFace mimeow_gl_FrontFace
#define glGenBuffers mimeow_gl_GenBuffers
#define glGenTextures mimeow_gl_GenTextures
#define glGenVertexArrays mimeow_gl_GenVertexArrays
#define glGenerateMipmap mimeow_gl_GenerateMipmap
#define glGetBooleanv mimeow_gl_GetBooleanv
#define glGetError mimeow_gl_GetError
#define glGetIntegerv mimeow_gl_GetIntegerv
#define glGetProgramInfoLog mimeow_gl_GetProgramInfoLog
#define glGetProgramiv mimeow_gl_GetProgramiv
#define glGetShaderInfoLog mimeow_gl_GetShaderInfoLog
#define glGetShaderiv mimeow_gl_GetShaderiv
#define glGetString mimeow_gl_GetString
#define glGetUniformBlockIndex mimeow_gl_GetUniformBlockIndex
#define glGetUniformLocation mimeow_gl_GetUniformLocation
#define glIsEnabled mimeow_gl_IsEnabled
#define glLinkProgram mimeow_gl_LinkProgram
#define glPixelStorei mimeow_gl_PixelStorei
#define glScissor mimeow_gl_Scissor
#define glShaderSource mimeow_gl_ShaderSource
#define glStencilFunc mimeow_gl_StencilFunc
#define glStencilMask mimeow_gl_StencilMask
#define glStencilOp mimeow_gl_StencilOp
#define glStencilOpSeparate mimeow_gl_StencilOpSeparate
#define glTexImage2D mimeow_gl_TexImage2D
#define glTexParameteri mimeow_gl_TexParameteri
#define glTexSubImage2D mimeow_gl_TexSubImage2D
#define glUniform1i mimeow_gl_Uniform1i
#define glUniform2fv mimeow_gl_Uniform2fv
#define glUniform4fv mimeow_gl_Uniform4fv
#define glUniformBlockBinding mimeow_gl_UniformBlockBinding
#define glUseProgram mimeow_gl_UseProgram
#define glVertexAttribPointer mimeow_gl_VertexAttribPointer
#define glViewport mimeow_gl_Viewport

int mimeow_gl_load(void);
void mimeow_gl_unload(void);
int mimeow_gl_gl3_available(void);
const char *mimeow_gl_error(void);

#endif
