package idle.miau.utility.shader;

import org.lwjgl.opengl.GL20;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public abstract class OutlineESPShader {
    private static final Logger LOGGER = LogManager.getLogger(OutlineESPShader.class);
    private static final String VERT = "#version 120\n" +
            "void main() {\n" +
            "  gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "  gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";

    protected int programId = -1;
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final String fragmentSource;
    private boolean initializationAttempted;

    public OutlineESPShader(String fragSrc) {
        fragmentSource = fragSrc;
    }

    private int compile(String source, int type) {
        int id = GL20.glCreateShader(type);
        if (id == 0) {
            return -1;
        }
        idle.miau.mcp.lwjgl.LegacyOpenGL.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == 0) {
            GL20.glDeleteShader(id);
            return -1;
        }
        return id;
    }

    private synchronized boolean initialize() {
        if (programId >= 0) {
            return true;
        }
        if (initializationAttempted || !ShaderCompatibility.isAvailable()) {
            return false;
        }

        initializationAttempted = true;
        int vertexShader = -1;
        int fragmentShader = -1;
        int program = -1;
        try {
            vertexShader = compile(VERT, GL20.GL_VERTEX_SHADER);
            fragmentShader = compile(fragmentSource, GL20.GL_FRAGMENT_SHADER);
            if (vertexShader < 0 || fragmentShader < 0) {
                return false;
            }

            program = GL20.glCreateProgram();
            if (program == 0) {
                return false;
            }
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                return false;
            }

            programId = program;
            onLink();
            return true;
        }
        catch (RuntimeException | LinkageError exception) {
            programId = -1;
            LOGGER.warn("Custom outline shaders are unavailable", exception);
            return false;
        }
        finally {
            deleteShader(vertexShader);
            deleteShader(fragmentShader);
            deleteProgram(programId < 0 ? program : -1);
        }
    }

    protected void cacheUniform(String name) {
        if (programId >= 0) uniforms.put(name, GL20.glGetUniformLocation(programId, name));
    }

    protected int uniform(String name) {
        return uniforms.getOrDefault(name, -1);
    }

    public abstract void onLink();

    public abstract void onUse();

    public void use() {
        if (initialize()) {
            onUse();
        }
    }

    public void stop() {
        if (programId >= 0 && ShaderCompatibility.isAvailable()) {
            GL20.glUseProgram(0);
        }
    }

    public boolean isValid() {
        return initialize();
    }

    private static void deleteShader(int shader) {
        if (shader <= 0) {
            return;
        }
        try {
            GL20.glDeleteShader(shader);
        }
        catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static void deleteProgram(int program) {
        if (program <= 0) {
            return;
        }
        try {
            GL20.glDeleteProgram(program);
        }
        catch (RuntimeException | LinkageError ignored) {
        }
    }
}
