package keystrokesmod.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ScriptSourceTransformer {
    private ScriptSourceTransformer() {
    }

    static final class ProcessedScript {
        final List<String> extractedImports;
        final String code;

        ProcessedScript(List<String> extractedImports, String code) {
            this.extractedImports = extractedImports;
            this.code = code;
        }
    }

    static ProcessedScript process(String source, String scriptName) {
        if (source == null || source.isEmpty()) {
            return new ProcessedScript(Collections.emptyList(), "");
        }
        String unwrapped = unwrapLegacyTemplate(source, scriptName);
        String[] rawLines = unwrapped.split("\r?\n", -1);
        List<String> imports = new ArrayList<>();
        StringBuilder transformedCode = new StringBuilder();

        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                transformedCode.append("\n");
            } else if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                String imp = trimmed.substring(7, trimmed.length() - 1).trim();
                if (!imp.startsWith("keystrokesmod.script.ScriptModule") && !imp.startsWith("net.minecraftforge.")) {
                    imports.add(imp);
                }
                transformedCode.append("\n");
            } else {
                transformedCode.append(line);
                if (i < rawLines.length - 1) {
                    transformedCode.append("\n");
                }
            }
        }

        String sanitized = sanitizeCommonTypos(transformedCode.toString());
        String blockedRewritten = rewriteBlockedCalls(sanitized);
        String finalCode = supportNestedTypes(blockedRewritten);
        return new ProcessedScript(imports, finalCode);
    }

    static String sanitizeCommonTypos(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        String cleaned = source.replaceAll("\\bwList<", "List<");
        cleaned = cleaned.replaceAll("\\binvntory\\b", "inventory");
        return cleaned;
    }

    public static String unwrapLegacyTemplate(String source, String scriptName) {
        if (source == null || (!source.contains("extends ScriptModule") && !source.contains("extends Module"))) {
            return source;
        }
        String[] rawLines = source.split("\r?\n", -1);
        List<String> out = new ArrayList<>();
        boolean insideClass = false;
        int classBraceDepth = 0;
        boolean insideConstructor = false;
        int constructorBraceDepth = 0;

        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import keystrokesmod.script.ScriptModule")
                    || trimmed.startsWith("import net.minecraftforge.")) {
                out.add("");
                continue;
            }

            if (!insideClass && trimmed.matches("^(?:public\\s+)?class\\s+[a-zA-Z0-9_$]+\\s+extends\\s+(?:ScriptModule|Module).*")) {
                insideClass = true;
                int opens = countChar(line, '{');
                int closes = countChar(line, '}');
                classBraceDepth = opens - closes;
                out.add("");
                continue;
            }

            if (insideClass && classBraceDepth == 0 && trimmed.startsWith("{")) {
                int opens = countChar(line, '{');
                int closes = countChar(line, '}');
                classBraceDepth += (opens - closes);
                out.add("");
                continue;
            }

            if (insideClass) {
                if (!insideConstructor) {
                    boolean isConstructor = false;
                    if (scriptName != null && !scriptName.isEmpty()) {
                        isConstructor = trimmed.matches("^(?:(?:public|protected|private)\\s+)?" + Pattern.quote(scriptName) + "\\s*\\([^)]*\\)\\s*(?:throws\\s+[^{;]+)?\\s*\\{?.*");
                    }
                    if (isConstructor) {
                        insideConstructor = true;
                        int opens = countChar(line, '{');
                        int closes = countChar(line, '}');
                        constructorBraceDepth = opens - closes;
                        if (constructorBraceDepth <= 0 && opens > 0) {
                            insideConstructor = false;
                        }
                        out.add("");
                        continue;
                    }
                } else {
                    int opens = countChar(line, '{');
                    int closes = countChar(line, '}');
                    constructorBraceDepth += (opens - closes);
                    if (constructorBraceDepth <= 0) {
                        insideConstructor = false;
                    }
                    out.add("");
                    continue;
                }

                if (trimmed.startsWith("super(") && trimmed.endsWith(");")) {
                    out.add("");
                    continue;
                }

                if (trimmed.equals("@Override") || trimmed.equals("@SubscribeEvent")) {
                    out.add("");
                    continue;
                }

                if (trimmed.matches(".*void\\s+onTick\\s*\\([^)]*\\).*")) {
                    line = line.replaceAll("void\\s+onTick\\s*\\([^)]*\\)", "void onPreUpdate()");
                }

                if (line.contains("event.phase")) {
                    line = line.replaceAll("event\\.phase\\s*!=\\s*(?:TickEvent\\.)?Phase\\.START\\s*\\|\\|\\s*", "");
                    line = line.replaceAll("if\\s*\\(\\s*event\\.phase\\s*!=\\s*(?:TickEvent\\.)?Phase\\.START\\s*\\)\\s*return\\s*;?", "");
                    line = line.replaceAll("if\\s*\\(\\s*event\\.phase\\s*==\\s*(?:TickEvent\\.)?Phase\\.END\\s*\\)\\s*return\\s*;?", "");
                }

                if (line.contains("mc.thePlayer == null") && !line.contains("mc ==")) {
                    line = line.replace("mc.thePlayer == null", "(mc == null || mc.thePlayer == null)");
                }

                int opens = countChar(line, '{');
                int closes = countChar(line, '}');
                classBraceDepth += (opens - closes);
                if (classBraceDepth <= 0) {
                    insideClass = false;
                    out.add("");
                    continue;
                }
            }

            out.add(line);
        }
        return String.join("\n", out);
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    static String rewriteBlockedCalls(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }
        String rewritten = replaceCall(source, "System.currentTimeMillis", "timeMillis");
        return replaceCall(rewritten, "System.nanoTime", "client.nanoTime");
    }

    private static String replaceCall(String source, String call, String replacement) {
        StringBuilder result = new StringBuilder(source.length());
        int copied = 0;
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (current == '"' || current == '\'') {
                index = skipQuotedValue(source, index + 1, current);
                continue;
            }
            if (current == '/' && index + 1 < length) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    index = skipLineComment(source, index + 2);
                    continue;
                }
                if (next == '*') {
                    index = skipBlockComment(source, index + 2);
                    continue;
                }
            }
            boolean atCall = source.startsWith(call, index)
                    && (index == 0 || !Character.isJavaIdentifierPart(source.charAt(index - 1)))
                    && index + call.length() + 1 < length
                    && source.charAt(index + call.length()) == '('
                    && source.charAt(index + call.length() + 1) == ')';
            if (atCall) {
                result.append(source, copied, index);
                result.append(replacement).append("()");
                index += call.length() + 2;
                copied = index;
                continue;
            }
            index++;
        }
        if (copied == 0) {
            return source;
        }
        result.append(source.substring(copied));
        return result.toString();
    }

    static String supportNestedTypes(String source) {
        if (source == null || source.isEmpty()) {
            return source;
        }

        List<Token> tokens = tokenize(source);
        Map<Integer, Integer> matchingBraces = findMatchingBraces(tokens);
        List<TypeDeclaration> declarations = findTypeDeclarations(tokens, matchingBraces);
        attachMemberParents(declarations);

        List<Integer> insertionPoints = new ArrayList<>();
        for (TypeDeclaration declaration : declarations) {
            if (declaration.eligibleMember && declaration.kind == TypeKind.CLASS
                    && becomesStatic(declaration) && !declaration.declaredStatic) {
                insertionPoints.add(declaration.keywordPosition);
            }
        }

        if (insertionPoints.isEmpty()) {
            return source;
        }

        insertionPoints.sort(Collections.reverseOrder());
        StringBuilder transformed = new StringBuilder(source);
        for (int insertionPoint : insertionPoints) {
            transformed.insert(insertionPoint, "static ");
        }
        return transformed.toString();
    }

    private static boolean becomesStatic(TypeDeclaration declaration) {
        if (declaration.kind != TypeKind.CLASS) {
            return true;
        }
        if (declaration.staticState != null) {
            return declaration.staticState;
        }

        declaration.staticState = declaration.declaredStatic;
        if (!declaration.staticState) {
            for (TypeDeclaration child : declaration.memberChildren) {
                if (child.eligibleMember && becomesStatic(child)) {
                    declaration.staticState = true;
                    break;
                }
            }
        }
        return declaration.staticState;
    }

    private static void attachMemberParents(List<TypeDeclaration> declarations) {
        declarations.sort(Comparator.comparingInt(declaration -> declaration.keywordToken));

        for (TypeDeclaration declaration : declarations) {
            TypeDeclaration enclosing = null;
            for (TypeDeclaration candidate : declarations) {
                if (candidate.keywordToken >= declaration.keywordToken) {
                    break;
                }
                if (candidate.bodyOpenToken < declaration.keywordToken
                        && candidate.bodyCloseToken > declaration.keywordToken
                        && (enclosing == null || candidate.bodyOpenToken > enclosing.bodyOpenToken)) {
                    enclosing = candidate;
                }
            }

            if (enclosing == null) {
                declaration.eligibleMember = declaration.depth == 0;
                continue;
            }

            boolean directMember = declaration.depth == enclosing.depth + 1;
            declaration.eligibleMember = directMember && enclosing.eligibleMember;
            if (directMember) {
                enclosing.memberChildren.add(declaration);
            }
        }
    }

    private static List<TypeDeclaration> findTypeDeclarations(List<Token> tokens,
                                                               Map<Integer, Integer> matchingBraces) {
        List<TypeDeclaration> declarations = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            TypeKind kind = typeKind(token.text);
            if (kind == null || isClassLiteral(tokens, i)) {
                continue;
            }

            int bodyOpen = findTypeBody(tokens, i);
            Integer bodyClose = matchingBraces.get(bodyOpen);
            if (bodyOpen == -1 || bodyClose == null) {
                continue;
            }

            TypeDeclaration declaration = new TypeDeclaration();
            declaration.kind = kind;
            declaration.keywordToken = i;
            declaration.keywordPosition = token.position;
            declaration.bodyOpenToken = bodyOpen;
            declaration.bodyCloseToken = bodyClose;
            declaration.depth = token.depth;
            declaration.declaredStatic = kind != TypeKind.CLASS || hasStaticModifier(tokens, i);
            declarations.add(declaration);
        }
        return declarations;
    }

    private static int findTypeBody(List<Token> tokens, int keywordToken) {
        int declarationDepth = tokens.get(keywordToken).depth;
        int parentheses = 0;
        int brackets = 0;

        for (int i = keywordToken + 1; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.depth < declarationDepth) {
                return -1;
            }
            if (token.depth != declarationDepth) {
                continue;
            }

            if ("(".equals(token.text)) {
                parentheses++;
            }
            else if (")".equals(token.text) && parentheses > 0) {
                parentheses--;
            }
            else if ("[".equals(token.text)) {
                brackets++;
            }
            else if ("]".equals(token.text) && brackets > 0) {
                brackets--;
            }
            else if ("{".equals(token.text) && parentheses == 0 && brackets == 0) {
                return i;
            }
            else if (";".equals(token.text) && parentheses == 0 && brackets == 0) {
                return -1;
            }
        }
        return -1;
    }

    private static boolean hasStaticModifier(List<Token> tokens, int keywordToken) {
        int declarationDepth = tokens.get(keywordToken).depth;
        for (int i = keywordToken - 1; i >= 0; i--) {
            Token token = tokens.get(i);
            if (token.depth < declarationDepth) {
                break;
            }
            if (token.depth > declarationDepth) {
                continue;
            }
            if (";".equals(token.text) || "{".equals(token.text) || "}".equals(token.text)) {
                break;
            }
            if ("static".equals(token.text)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isClassLiteral(List<Token> tokens, int tokenIndex) {
        return tokenIndex > 0
                && "class".equals(tokens.get(tokenIndex).text)
                && ".".equals(tokens.get(tokenIndex - 1).text);
    }

    private static TypeKind typeKind(String token) {
        if ("class".equals(token)) {
            return TypeKind.CLASS;
        }
        if ("enum".equals(token)) {
            return TypeKind.ENUM;
        }
        if ("interface".equals(token)) {
            return TypeKind.INTERFACE;
        }
        return null;
    }

    private static Map<Integer, Integer> findMatchingBraces(List<Token> tokens) {
        Map<Integer, Integer> matching = new HashMap<>();
        Deque<Integer> openings = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).text;
            if ("{".equals(token)) {
                openings.push(i);
            }
            else if ("}".equals(token) && !openings.isEmpty()) {
                matching.put(openings.pop(), i);
            }
        }
        return matching;
    }

    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int depth = 0;
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);

            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()) {
                char next = source.charAt(index + 1);
                if (next == '/') {
                    index = skipLineComment(source, index + 2);
                    continue;
                }
                if (next == '*') {
                    index = skipBlockComment(source, index + 2);
                    continue;
                }
            }
            if (current == '"' || current == '\'') {
                index = skipQuotedValue(source, index + 1, current);
                continue;
            }
            if (Character.isJavaIdentifierStart(current)) {
                int start = index++;
                while (index < source.length() && Character.isJavaIdentifierPart(source.charAt(index))) {
                    index++;
                }
                tokens.add(new Token(source.substring(start, index), start, depth));
                continue;
            }

            if (current == '}') {
                depth = Math.max(0, depth - 1);
            }
            tokens.add(new Token(String.valueOf(current), index, depth));
            if (current == '{') {
                depth++;
            }
            index++;
        }
        return tokens;
    }

    private static int skipLineComment(String source, int index) {
        while (index < source.length() && source.charAt(index) != '\n' && source.charAt(index) != '\r') {
            index++;
        }
        return index;
    }

    private static int skipBlockComment(String source, int index) {
        while (index + 1 < source.length()) {
            if (source.charAt(index) == '*' && source.charAt(index + 1) == '/') {
                return index + 2;
            }
            index++;
        }
        return source.length();
    }

    private static int skipQuotedValue(String source, int index, char quote) {
        boolean escaped = false;
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (escaped) {
                escaped = false;
            }
            else if (current == '\\') {
                escaped = true;
            }
            else if (current == quote) {
                break;
            }
        }
        return index;
    }

    private enum TypeKind {
        CLASS,
        ENUM,
        INTERFACE
    }

    private static final class Token {
        private final String text;
        private final int position;
        private final int depth;

        private Token(String text, int position, int depth) {
            this.text = text;
            this.position = position;
            this.depth = depth;
        }
    }

    private static final class TypeDeclaration {
        private TypeKind kind;
        private int keywordToken;
        private int keywordPosition;
        private int bodyOpenToken;
        private int bodyCloseToken;
        private int depth;
        private boolean declaredStatic;
        private boolean eligibleMember;
        private Boolean staticState;
        private final List<TypeDeclaration> memberChildren = new ArrayList<>();
    }
}
