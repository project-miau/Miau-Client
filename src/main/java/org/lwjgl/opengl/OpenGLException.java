package org.lwjgl.opengl;


public class OpenGLException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OpenGLException() {
    }

    public OpenGLException(String message) {
        super(message);
    }

    public OpenGLException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenGLException(Throwable cause) {
        super(cause);
    }

    public OpenGLException(int glErrorCode) {
        this("OpenGL error " + glErrorCode);
    }
}
