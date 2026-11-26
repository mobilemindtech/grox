package org.grox.view.gsp

/**
 * Gera o código Groovy de uma GSP (script com base {@link GspPage}).
 *
 * Tags de sintaxe ({@code g:if/elseif/else/unless/while/each/set}) viram
 * estruturas de controle; as demais viram {@code invokeTag(ns, nome, attrs, corpo)}.
 * Cada linha gerada termina com {@code //@gsp:N} para mapear erros à linha da GSP.
 */
class GspCompiler {

    static final Set<String> SYNTAX_TAGS = ['if', 'elseif', 'else', 'unless', 'while', 'each', 'set'] as Set
    static final String LINE_MARK = '//@gsp:'

    static class Result {
        String source
        List<String> texts
        String contentType
    }

    private static class Frame {
        TagStartNode tag
        String close
    }

    private final String view
    private final StringBuilder code = new StringBuilder()
    private final List<String> imports = []
    private final List<String> texts = []
    private final Deque<Frame> stack = new ArrayDeque<>()
    private String contentType
    private int counter

    GspCompiler(String view) {
        this.view = view
    }

    static Result compile(List<GspNode> nodes, String view) {
        new GspCompiler(view).run(nodes)
    }

    Result run(List<GspNode> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            GspNode n = nodes[i]
            if (n instanceof TextNode && isIgnorableBeforeElse(nodes, i)) continue
            switch (n) {
                case TextNode -> text((TextNode) n)
                case ExprNode -> emit("printEncoded(${wrap(((ExprNode) n).code)})", n.line)
                case ScriptletNode -> scriptlet((ScriptletNode) n)
                case DirectiveNode -> directive((DirectiveNode) n)
                case TagStartNode -> tagStart((TagStartNode) n, previousSignificant(nodes, i))
                case TagEndNode -> tagEnd((TagEndNode) n)
            }
        }
        if (stack) {
            TagStartNode open = stack.peek().tag
            throw new GspException(view, open.line, "tag <${open.qualifiedName}> não foi fechada")
        }
        String header = imports.collect { "import ${it}\n" }.join('')
        new Result(source: header + code.toString(), texts: texts, contentType: contentType)
    }

    /** Espaços entre {@code </g:if>} e {@code <g:else>} são descartados. */
    private static boolean isIgnorableBeforeElse(List<GspNode> nodes, int i) {
        if (!((TextNode) nodes[i]).text.trim().isEmpty() || i + 1 >= nodes.size() || i == 0) return false
        GspNode next = nodes[i + 1]
        GspNode prev = nodes[i - 1]
        next instanceof TagStartNode && next.namespace == 'g' && next.name in ['else', 'elseif'] &&
                ((prev instanceof TagEndNode && prev.namespace == 'g' && prev.name in ['if', 'elseif', 'unless']) ||
                        (prev instanceof TagStartNode && prev.selfClosing))
    }

    private static GspNode previousSignificant(List<GspNode> nodes, int i) {
        for (int k = i - 1; k >= 0; k--) {
            GspNode n = nodes[k]
            if (n instanceof TextNode && ((TextNode) n).text.trim().isEmpty()) continue
            return n
        }
        null
    }

    private void emit(String line, int gspLine) {
        code << line << ' ' << LINE_MARK << gspLine << '\n'
    }

    private void text(TextNode n) {
        texts << n.text
        emit("printText(${texts.size() - 1})", n.line)
    }

    private void scriptlet(ScriptletNode n) {
        List<String> lines = n.code.readLines()
        lines.eachWithIndex { String l, int k -> emit(l, n.line + k) }
    }

    private void directive(DirectiveNode d) {
        d.attrs.import?.split(/[;,]/)*.trim()?.findAll()?.each { imports << it }
        if (d.attrs.contentType) contentType = d.attrs.contentType
    }

    private void tagStart(TagStartNode t, GspNode previous) {
        if (t.namespace == 'g' && t.name in SYNTAX_TAGS) {
            syntaxTag(t, previous)
            return
        }
        String call = "invokeTag(${quote(t.namespace)}, ${quote(t.name)}, ${attrsMap(t)}"
        if (t.selfClosing) {
            emit(call + ', null)', t.line)
        } else {
            emit(call + ", { Object... __body${counter++} ->", t.line)
            stack.push(new Frame(tag: t, close: '})'))
        }
    }

    private void syntaxTag(TagStartNode t, GspNode previous) {
        switch (t.name) {
            case 'if' -> open(t, "if (${truth(t, 'test')}) {", '}')
            case 'unless' -> open(t, "if (!(${truth(t, 'test')})) {", '}')
            case 'while' -> open(t, "while (${truth(t, 'test')}) {", '}')
            case 'elseif', 'else' -> {
                boolean afterIf = previous instanceof TagEndNode && previous.namespace == 'g' && previous.name in ['if', 'elseif', 'unless']
                if (!afterIf) throw new GspException(view, t.line, "<g:${t.name}> deve vir logo após </g:if> ou </g:elseif>")
                // reabre o bloco do if anterior: remove o fechamento emitido
                removeLastClose()
                open(t, t.name == 'else' ? '} else {' : "} else if (${truth(t, 'test')}) {", '}')
            }
            case 'each' -> {
                String var = eachVar(t)
                String status = t.attrs.status?.text
                String idx = "__i${counter++}"
                if (!(var ==~ /[A-Za-z_]\w*/)) throw new GspException(view, t.line, "var inválido em <g:each>: ${var}")
                String coll = eachCollection(t)
                if (var == 'it' && stack.any { it.tag.name == 'each' && it.tag.namespace == 'g' && eachVar(it.tag) == 'it' }) {
                    // <g:each> sem var dentro de outro sem var: closure com o it implícito (um for redeclararia o it)
                    open(t, "int ${idx} = 0; (${coll} ?: []).each { " + (status ? "def ${status} = ${idx}; " : '') + "${idx}++", '}')
                } else {
                    open(t, "int ${idx} = 0; for (${var} in (${coll} ?: [])) { " + (status ? "def ${status} = ${idx}; " : '') + "${idx}++", '}')
                }
            }
            case 'set' -> {
                String var = t.attrs.var?.text
                if (!var) throw new GspException(view, t.line, '<g:set> exige o atributo var')
                if (t.attrs.containsKey('value') || t.selfClosing) {
                    emit("setPageVariable(${quote(var)}, ${t.attrs.value ? expr(t.attrs.value) : 'null'})", t.line)
                    if (!t.selfClosing) stack.push(new Frame(tag: t, close: ''))
                } else {
                    emit("setPageVariable(${quote(var)}, captureBody({ ->", t.line)
                    stack.push(new Frame(tag: t, close: '}))'))
                }
            }
        }
    }

    private void open(TagStartNode t, String line, String close) {
        emit(line, t.line)
        if (t.selfClosing) emit(close, t.line)
        else stack.push(new Frame(tag: t, close: close))
    }

    private void removeLastClose() {
        String marker = '\n'
        int end = code.length() - 1
        int start = code.lastIndexOf(marker, end - 1) + 1
        String last = code.substring(start, end)
        if (!last.startsWith('}')) throw new GspException(view, 0, 'estrutura if/else inválida')
        code.setLength(start)
    }

    private void tagEnd(TagEndNode n) {
        if (!stack) throw new GspException(view, n.line, "</${n.qualifiedName}> sem tag de abertura")
        Frame f = stack.pop()
        if (f.tag.qualifiedName != n.qualifiedName) {
            throw new GspException(view, n.line, "esperado </${f.tag.qualifiedName}> (aberta na linha ${f.tag.line}), encontrado </${n.qualifiedName}>")
        }
        if (f.close) emit(f.close, n.line)
    }

    /** {@code var="x"} ou, como o Grails aceita, {@code var="${x}"}; sem var, {@code it}. */
    private static String eachVar(TagStartNode t) {
        AttrValue v = t.attrs.var
        if (v == null) return 'it'
        if (v.singleExpression) return ((ExprNode) v.parts[0]).code.trim()
        v.text
    }

    /** {@code in="${x?.lista?}"}: o "?" final do scaffold antigo do Grails é removido, como no Grails. */
    private String eachCollection(TagStartNode t) {
        AttrValue v = t.attrs['in']
        if (v?.singleExpression) {
            String code = ((ExprNode) v.parts[0]).code.trim()
            if (code.endsWith('?') && !code.endsWith('??')) return wrap(code.substring(0, code.length() - 1))
        }
        required(t, 'in')
    }

    private String required(TagStartNode t, String attr) {
        AttrValue v = t.attrs[attr]
        if (v == null) throw new GspException(view, t.line, "<${t.qualifiedName}> exige o atributo ${attr}")
        expr(v)
    }

    /** Condição de if/elseif/unless/while; {@code env="development"} (sozinho ou com test) como no Grails. */
    private String truth(TagStartNode t, String attr) {
        AttrValue env = t.name in ['if', 'elseif', 'unless'] ? t.attrs.env : null
        if (env == null) return required(t, attr)
        String envTest = "(org.grox.core.Env.currentName == ${expr(env)})"
        t.attrs.containsKey(attr) ? "${envTest} && (${required(t, attr)})" : envTest
    }

    private String attrsMap(TagStartNode t) {
        if (!t.attrs) return '[:]'
        '[' + t.attrs.collect { k, v -> "${quote(k)}: ${expr(v)}" }.join(', ') + ']'
    }

    /** Expressão Groovy para o valor do atributo: objeto se for só {@code ${...}}, senão String. */
    static String expr(AttrValue v) {
        if (v.singleExpression) return wrap(((ExprNode) v.parts[0]).code)
        // como o Grails: valor literal no formato de lista/mapa é avaliado (noSelection="['':'-']", params="[a: 1]")
        if (v.allLiteral && v.text.trim().startsWith('[') && v.text.trim().endsWith(']')) return wrap(v.text.trim())
        if (v.allLiteral) return quote(v.text)
        '(' + v.parts.collect { it instanceof ExprNode ? "String.valueOf(${wrap(((ExprNode) it).code)})" : quote((String) it) }.join(' + ') + ')'
    }

    static String wrap(String c) {
        c.contains('\n') ? "(\n${c}\n)" : "(${c})"
    }

    static String quote(String s) {
        StringBuilder sb = new StringBuilder("'")
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' as char -> sb << '\\\\'
                case '\'' as char -> sb << "\\'"
                case '\n' as char -> sb << '\\n'
                case '\r' as char -> sb << '\\r'
                case '\t' as char -> sb << '\\t'
                default -> sb << c
            }
        }
        sb << "'"
        sb.toString()
    }
}
