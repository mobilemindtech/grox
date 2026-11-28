package org.grox.http

@groovy.transform.CompileStatic
class Html {

    /** Atributo da requisição com as strings já codificadas pelos codecs (conjunto por identidade). */
    static final String ENCODED_ATTR = 'grox.encodedStrings'

    /**
     * Registra uma string já codificada ({@code encodeAsHTML}, {@code encodeAsJavaScript}, {@code encodeAsURL}) na
     * requisição atual, como o {@code EncodingStateRegistry} do Grails: o {@link #escape} a devolve como está. Fora
     * de uma requisição, não faz nada. Só para saídas sem {@code < > " '} crus.
     */
    static String markEncoded(String value) {
        if (!value) return value
        GroxRequest req = CurrentRequest.orNull()?.request
        if (req == null) return value
        Set<String> encoded = (Set<String>) req.getAttribute(ENCODED_ATTR)
        if (encoded == null) {
            encoded = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<String, Boolean>()))
            req.setAttribute(ENCODED_ATTR, encoded)
        }
        encoded.add(value)
        value
    }

    /** A string veio de um codec nesta requisição. */
    static boolean isEncoded(Object value) {
        if (!(value instanceof String)) return false
        RequestScope scope = CurrentRequest.orNull()
        Set<String> encoded = scope?.request == null ? null : (Set<String>) scope.request.getAttribute(ENCODED_ATTR)
        encoded != null && encoded.contains(value)
    }

    static String escape(Object value) {
        if (value == null) return ''
        if (value instanceof SafeHtml) return value.toString()
        if (isEncoded(value)) return (String) value
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
        if (attrs == null) return ''
        // laço, não each: a closure era chamada pelo runtime a cada atributo
        for (Object o : attrs.entrySet()) {
            Map.Entry e = (Map.Entry) o
            Object v = e.value
            // instanceof: 'v == false' com String passa por reflexão (e exceção) a cada atributo
            if (v == null || (v instanceof Boolean && !((Boolean) v).booleanValue())) continue
            sb.append((char) ' ').append(String.valueOf(e.key))
            if (!(v instanceof Boolean)) sb.append('="').append(escape(v)).append((char) '"')
        }
        sb.toString()
    }
}
