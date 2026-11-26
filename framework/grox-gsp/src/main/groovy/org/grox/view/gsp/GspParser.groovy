package org.grox.view.gsp

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Parser de GSP: texto, {@code ${...}}, {@code <% %>}, {@code <%= %>},
 * {@code <%@ %>}, comentários ({@code %{-- --}%}, {@code <%-- --%>}) e tags
 * {@code <ns:nome attr="...">} dos namespaces registrados.
 */
class GspParser {

    private static final Pattern TAG_START = ~/\G<([a-zA-Z]\w*):([a-zA-Z_][\w.\-]*)/
    private static final Pattern TAG_END = ~/\G<\/([a-zA-Z]\w*):([a-zA-Z_][\w.\-]*)\s*>/
    private static final Pattern ATTR_NAME = ~/\G[\w:\-.@\[\]]+/

    private final String src
    private final String view
    private final Set<String> namespaces
    private final List<GspNode> nodes = []
    private final StringBuilder text = new StringBuilder()
    private int textLine = 1
    private int pos
    private int[] lineStarts

    GspParser(String src, Set<String> namespaces, String view = null) {
        this.src = src
        this.namespaces = namespaces
        this.view = view
    }

    static List<GspNode> parse(String src, Set<String> namespaces, String view = null) {
        new GspParser(src, namespaces, view).parse()
    }

    List<GspNode> parse() {
        indexLines()
        while (pos < src.length()) {
            if (src.startsWith('%{--', pos)) {
                skipTo('--}%', 'comentário %{-- sem fechamento')
            } else if (src.startsWith('<%--', pos)) {
                skipTo('--%>', 'comentário <%-- sem fechamento')
            } else if (src.startsWith('<%@', pos)) {
                int start = pos
                String body = readUntil(pos + 3, '%>', 'diretiva <%@ sem fechamento')
                flushText()
                nodes << directive(body.trim(), lineOf(start))
            } else if (src.startsWith('<%=', pos)) {
                int start = pos
                String code = readUntil(pos + 3, '%>', 'expressão <%= sem fechamento')
                flushText()
                nodes << new ExprNode(code: code.trim(), line: lineOf(start))
            } else if (src.startsWith('<%', pos)) {
                int start = pos
                String code = readUntil(pos + 2, '%>', 'scriptlet <% sem fechamento')
                flushText()
                nodes << new ScriptletNode(code: code, line: lineOf(start))
            } else if (src.startsWith('${', pos)) {
                int start = pos
                String code = readExpression(pos + 2)
                flushText()
                nodes << new ExprNode(code: code.trim(), line: lineOf(start))
            } else if (src.startsWith('</', pos) && tryTagEnd()) {
                // consumido
            } else if (src.charAt(pos) == '<' as char && tryTagStart()) {
                // consumido
            } else {
                if (text.length() == 0) textLine = lineOf(pos)
                text.append(src.charAt(pos++))
            }
        }
        flushText()
        nodes
    }

    private void flushText() {
        if (text.length()) {
            nodes << new TextNode(text: text.toString(), line: textLine)
            text.setLength(0)
        }
    }

    private void skipTo(String end, String error) {
        int i = src.indexOf(end, pos)
        if (i < 0) throw new GspException(view, lineOf(pos), error)
        pos = i + end.length()
    }

    private String readUntil(int from, String end, String error) {
        int i = src.indexOf(end, from)
        if (i < 0) throw new GspException(view, lineOf(pos), error)
        pos = i + end.length()
        src.substring(from, i)
    }

    /** Lê até o {@code }} que fecha a expressão, respeitando chaves e strings. */
    private String readExpression(int from) {
        int end = scanExpression(from)
        if (end < 0) throw new GspException(view, lineOf(pos), 'expressão ${ sem fechamento')
        pos = end + 1
        src.substring(from, end)
    }

    /** @return índice do '}' final ou -1 */
    private int scanExpression(int from) {
        int depth = 1
        int i = from
        while (i < src.length()) {
            char c = src.charAt(i)
            if (c == '\'' as char || c == '"' as char) {
                i = skipString(i)
                continue
            }
            if (c == '{' as char) depth++
            else if (c == '}' as char) {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        -1
    }

    private int skipString(int i) {
        char q = src.charAt(i)
        boolean triple = src.startsWith("${q}${q}${q}", i)
        String close = triple ? "${q}${q}${q}" : "${q}"
        i += close.length()
        while (i < src.length()) {
            char c = src.charAt(i)
            if (c == '\\' as char) {
                i += 2
                continue
            }
            if (src.startsWith(close, i)) return i + close.length()
            if (!triple && c == '\n' as char) return i
            i++
        }
        i
    }

    private boolean tryTagEnd() {
        Matcher m = TAG_END.matcher(src)
        m.region(pos, src.length())
        if (!m.lookingAt() || !(m.group(1) in namespaces)) return false
        flushText()
        nodes << new TagEndNode(namespace: m.group(1), name: m.group(2), line: lineOf(pos))
        pos = m.end()
        true
    }

    private boolean tryTagStart() {
        Matcher m = TAG_START.matcher(src)
        m.region(pos, src.length())
        if (!m.lookingAt() || !(m.group(1) in namespaces)) return false
        int start = pos
        flushText()
        def tag = new TagStartNode(namespace: m.group(1), name: m.group(2), line: lineOf(start))
        pos = m.end()
        while (true) {
            skipWhitespace()
            if (pos >= src.length()) throw new GspException(view, tag.line, "tag <${tag.qualifiedName}> sem fechamento")
            if (src.startsWith('/>', pos)) {
                tag.selfClosing = true
                pos += 2
                break
            }
            if (src.charAt(pos) == '>' as char) {
                pos++
                break
            }
            if (src.charAt(pos) == '<' as char) {
                // como o Grails, tolera tag sem '>' (ex.: <g:link ...  seguido de <i>): fecha aqui
                org.slf4j.LoggerFactory.getLogger(GspParser).warn("${view}:${lineOf(pos)}: <${tag.qualifiedName}> sem '>' (fechada antes do próximo '<')")
                break
            }
            if (src.charAt(pos) == ',' as char) {
                // como o Grails, tolera vírgula entre atributos (ex.: params="..." , action="...")
                org.slf4j.LoggerFactory.getLogger(GspParser).warn("${view}:${lineOf(pos)}: vírgula entre atributos de <${tag.qualifiedName}> (ignorada)")
                pos++
                continue
            }
            Matcher an = ATTR_NAME.matcher(src)
            an.region(pos, src.length())
            if (!an.lookingAt()) throw new GspException(view, lineOf(pos), "atributo inválido em <${tag.qualifiedName}>")
            String name = an.group()
            pos = an.end()
            skipWhitespace()
            if (pos < src.length() && src.charAt(pos) == '=' as char) {
                pos++
                skipWhitespace()
                tag.attrs[name] = readAttrValue(tag)
            } else {
                tag.attrs[name] = new AttrValue(parts: [''])
            }
        }
        nodes << tag
        true
    }

    private AttrValue readAttrValue(TagStartNode tag) {
        char q = src.charAt(pos)
        if (q != '"' as char && q != '\'' as char) throw new GspException(view, lineOf(pos), "valor de atributo sem aspas em <${tag.qualifiedName}>")
        pos++
        def value = new AttrValue()
        StringBuilder lit = new StringBuilder()
        while (true) {
            if (pos >= src.length()) throw new GspException(view, tag.line, "atributo sem fechamento em <${tag.qualifiedName}>")
            char c = src.charAt(pos)
            if (c == q && closesAttribute(pos + 1)) {
                pos++
                break
            }
            if (src.startsWith('${', pos)) {
                if (lit.length()) {
                    value.parts << lit.toString()
                    lit.setLength(0)
                }
                int line = lineOf(pos)
                int end = scanExpression(pos + 2)
                if (end < 0) throw new GspException(view, line, 'expressão ${ sem fechamento em atributo')
                value.parts << new ExprNode(code: src.substring(pos + 2, end).trim(), line: line)
                pos = end + 1
                continue
            }
            lit.append(c)
            pos++
        }
        if (lit.length() || !value.parts) value.parts << lit.toString()
        value
    }

    /**
     * A aspa fecha o valor se vier seguida de espaço, {@code >}, {@code />}, vírgula ou fim. Senão faz parte dele,
     * como o Grails tolera: {@code params='[a: x ?: '']'}.
     */
    private boolean closesAttribute(int next) {
        if (next >= src.length()) return true
        char n = src.charAt(next)
        Character.isWhitespace(n) || n == '>' as char || n == '/' as char || n == ',' as char || n == '<' as char
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++
    }

    private DirectiveNode directive(String body, int line) {
        def m = body =~ /^(\w+)/
        def d = new DirectiveNode(name: m.find() ? m.group(1) : 'page', line: line)
        def attrs = body =~ /(\w+)\s*=\s*("([^"]*)"|'([^']*)')/
        while (attrs.find()) d.attrs[attrs.group(1)] = attrs.group(3) != null ? attrs.group(3) : attrs.group(4)
        d
    }

    private void indexLines() {
        List<Integer> starts = [0]
        for (int i = 0; i < src.length(); i++) if (src.charAt(i) == '\n' as char) starts << i + 1
        lineStarts = starts as int[]
    }

    int lineOf(int offset) {
        int idx = Arrays.binarySearch(lineStarts, offset)
        idx >= 0 ? idx + 1 : -idx - 1
    }
}
