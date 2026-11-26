package org.grox.http

@groovy.transform.CompileStatic
class Html {

    static String escape(Object value) {
        if (value == null) return ''
        if (value instanceof SafeHtml) return value.toString()
        String s = value.toString()
        StringBuilder sb = null
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i)
            // comparações de char: um switch com 'x' as char passa por isCase (despacho dinâmico) por caractere
            String rep = c == (char) '&' ? '&amp;' : c == (char) '<' ? '&lt;' : c == (char) '>' ? '&gt;' :
                    c == (char) '"' ? '&quot;' : c == (char) '\'' ? '&#39;' : null
            if (rep != null) {
                if (sb == null) sb = new StringBuilder(s.length() + 16).append(s, 0, i)
                sb.append(rep)
            } else if (sb != null) {
                sb.append(c)
            }
        }
        sb == null ? s : sb.toString()
    }

    /** Atributos HTML ({@code k="v"}); null é omitido, true vira atributo booleano. */
    static String attributes(Map attrs) {
        StringBuilder sb = new StringBuilder()
        attrs?.each { k, v ->
            // instanceof: 'v == false' com String passa por reflexão (e exceção) a cada atributo
            if (v == null || (v instanceof Boolean && !v)) return
            sb << ' ' << k
            if (!(v instanceof Boolean)) sb << '="' << escape(v) << '"'
        }
        sb.toString()
    }
}
