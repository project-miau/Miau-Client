package keystrokesmod.script;

import idle.miau.utility.Utils;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.util.Locale;

public class ScriptDiagnosticListener implements DiagnosticListener<JavaFileObject> {
    private final Script script;

    public ScriptDiagnosticListener() {
        this(null);
    }

    public ScriptDiagnosticListener(Script script) {
        this.script = script;
    }

    @Override
    public void report(final javax.tools.Diagnostic<? extends JavaFileObject> diagnostic) {
        final String message = diagnostic.getMessage(null);
        if (message.contains("SpongePowered")) {
            return;
        }
        System.err.println("[script-compile] " + diagnostic.getLineNumber() + ": " + message);
        String cleanMessage = message;
        int newlineIdx = cleanMessage.indexOf('\n');
        if (newlineIdx != -1) {
            cleanMessage = cleanMessage.substring(0, newlineIdx);
        }
        long lineNum = diagnostic.getLineNumber();
        if (diagnostic.getSource() instanceof JavaSourceFromString) {
            lineNum -= ((JavaSourceFromString) diagnostic.getSource()).extraLines;
        }
        if (lineNum <= 0) {
            lineNum = 1;
        }

        String errDesc = "Line " + lineNum + ": " + cleanMessage;
        if (script != null) {
            script.lastError = errDesc;
        }

        if (diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
            String scriptName = script != null ? script.name : (diagnostic.getSource() != null ? Utils.extractFileName(((JavaSourceFromString) diagnostic.getSource()).name) : "Script");
            Utils.sendMessage("&c[Script " + scriptName + "] &7" + errDesc);
        }

        if (diagnostic.getSource() != null) {
            Utils.sendDebugMessage("§cError loading script §b" + Utils.extractFileName(((JavaSourceFromString) diagnostic.getSource()).name));
        }
        final JavaFileObject javaFileObject = diagnostic.getSource();
        if (javaFileObject != null) {
            int indentIndex = message.indexOf("\n");
            String error = diagnostic.getMessage(Locale.getDefault());
            Utils.sendDebugMessage(" §7err: §c" + (indentIndex == -1 ? error : error.substring(0, indentIndex)));
            Utils.sendDebugMessage(" §7line: §c" + lineNum);
            try {
                String sourceContent = javaFileObject.getCharContent(true).toString();
                int startPos = (int) diagnostic.getStartPosition();
                int endPos = (int) diagnostic.getEndPosition();
                if (startPos >= 0 && endPos >= startPos && startPos < sourceContent.length()) {
                    int safeEnd = Math.min(endPos, sourceContent.length());
                    int srcIndentIndex = sourceContent.indexOf("\n", startPos);
                    if (srcIndentIndex != -1 && srcIndentIndex >= startPos) {
                        Utils.sendDebugMessage(" §7src: §c" + sourceContent.substring(startPos, srcIndentIndex));
                    }
                    else {
                        Utils.sendDebugMessage(" §7src: §c" + sourceContent.substring(startPos, safeEnd));
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
