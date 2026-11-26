package org.grox.routing

import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Um mapeamento do DSL do Grails, ex. {@code "/$controller/$action?/$id?(.$format)?"}.
 * Variáveis chegam do DSL delimitadas por {@link #MARK}.
 */
@groovy.transform.CompileStatic
class UrlMapping {

    static final String MARK = '\u0000'

    static class Var {
        String name
        boolean optional
        boolean wildcard
    }

    String pattern
    /** Cada segmento é uma lista de partes: String literal ou {@link Var}. */
    List<List<Object>> segments = []
    Var formatVar
    Pattern regex
    List<String> varNames = []
    /** controller, action (String ou Map verbo→ação), view, redirect, params... */
    Map<String, Object> attrs = [:]
    String httpMethod
    Map<String, Map> constraints = [:]
    Integer statusCode
    int order
    int literalWeight

    static UrlMapping parse(String raw) {
        def m = new UrlMapping(pattern: raw.replaceAll(/\u0000(\w+)\u0000/, '\\$$1'))
        String s = raw
        Matcher fm = s =~ /\(\.\u0000(\w+)\u0000\)\?$/
        if (fm.find()) {
            m.formatVar = new Var(name: fm.group(1), optional: true)
            s = s.substring(0, fm.start())
        }
        StringBuilder rx = new StringBuilder('^')
        for (String seg : s.split('/')) {
            if (!seg) continue
            if (seg == '**') {
                m.segments << ([seg] as List<Object>)
                rx << '(?:/.*)?'
                continue
            }
            if (seg == '*') {
                m.segments << ([seg] as List<Object>)
                rx << '/[^/]+'
                continue
            }
            List<Object> parts = parseSegment(seg)
            m.segments << parts
            if (parts.size() == 1 && parts[0] instanceof Var) {
                Var v = (Var) parts[0]
                m.varNames << v.name
                if (v.wildcard) rx << (v.optional ? '(?:/(.*))?' : '/(.+)')
                else if (v.optional) rx << '(?:/([^/]+?))?'
                else rx << '/([^/]+?)'
            } else {
                rx << '/'
                for (Object p : parts) {
                    if (p instanceof Var) {
                        m.varNames << ((Var) p).name
                        rx << '([^/]+?)'
                    } else {
                        rx << Pattern.quote((String) p)
                        m.literalWeight += ((String) p).length()
                    }
                }
            }
        }
        if (m.formatVar) {
            m.varNames << m.formatVar.name
            rx << '(?:\\.([^/.]+))?'
        }
        rx << (m.segments.empty ? '/$' : '/?$')
        m.regex = Pattern.compile(rx.toString())
        m
    }

    private static List<Object> parseSegment(String seg) {
        List<Object> parts = []
        String[] tokens = seg.split(MARK, -1)
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i]
            if (i % 2 == 1) {
                parts << new Var(name: tok)
                continue
            }
            if (i > 0 && parts && parts.last() instanceof Var) {
                Var last = (Var) parts.last()
                if (tok.startsWith('**')) {
                    last.wildcard = true
                    tok = tok.substring(2)
                } else if (tok.startsWith('?')) {
                    last.optional = true
                    tok = tok.substring(1)
                }
            }
            if (tok) parts << tok
        }
        parts
    }

    /** Retorna as variáveis capturadas ou null se o caminho não casar. */
    Map<String, String> match(String path) {
        if (regex == null) return null
        Matcher mt = regex.matcher(path)
        if (!mt.matches()) return null
        Map<String, String> vars = [:]
        for (int i = 0; i < varNames.size(); i++) {
            String g = mt.group(i + 1)
            if (g != null) vars[varNames[i]] = g
        }
        for (Map.Entry<String, Map> c : constraints.entrySet()) {
            Object rule = c.value?.matches
            String value = vars[c.key]
            if (rule != null && value != null && !(value ==~ rule.toString())) return null
        }
        vars
    }

    /** Gera a URL a partir dos valores; null se faltar variável obrigatória. */
    String reverse(Map<String, Object> values, Set<String> used) {
        if (regex == null) return null
        StringBuilder sb = new StringBuilder()
        outer:
        for (List<Object> parts : segments) {
            StringBuilder seg = new StringBuilder()
            for (Object p : parts) {
                if (p == '**' || p == '*') return null
                if (p instanceof Var) {
                    Var v = (Var) p
                    Object value = values[v.name]
                    if (value == null || value.toString().empty) {
                        if (v.optional) break outer
                        return null
                    }
                    // curinga (**) aceita barras: codifica segmento por segmento
                    seg << (v.wildcard ? value.toString().split('/', -1).collect { encode(it) }.join('/') : encode(value.toString()))
                    used << v.name
                } else {
                    seg << p
                }
            }
            sb << '/' << seg
        }
        if (formatVar && values[formatVar.name]) {
            sb << '.' << encode(values[formatVar.name].toString())
            used << formatVar.name
        }
        sb.length() ? sb.toString() : '/'
    }

    static String encode(String s) {
        URLEncoder.encode(s, StandardCharsets.UTF_8).replace('+', '%20')
    }

    @Override
    String toString() {
        (httpMethod ? "$httpMethod " : '') + (statusCode ?: pattern) + " -> $attrs"
    }
}
