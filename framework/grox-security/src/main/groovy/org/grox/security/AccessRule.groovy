package org.grox.security

import java.util.regex.Pattern

/** Regra do {@code interceptUrlMap}: padrão Ant, método HTTP opcional e lista de acesso. */
class AccessRule {

    final String pattern
    final Pattern regex
    final String httpMethod
    final List<String> access

    AccessRule(String pattern, String httpMethod, List<String> access) {
        this.pattern = pattern
        this.regex = Pattern.compile(antToRegex(pattern))
        this.httpMethod = httpMethod?.toUpperCase()
        this.access = access
        access.each { AccessExpression.validate(it) }
    }

    boolean matches(String path, String method) {
        (httpMethod == null || httpMethod == method || (httpMethod == 'GET' && method == 'HEAD')) && regex.matcher(path).matches()
    }

    /** {@code /admin/**} (também casa {@code /admin}), {@code /*.css}, {@code /usuario/?}. */
    static String antToRegex(String pattern) {
        if (pattern == '/**' || pattern == '**') return '.*'
        String p = pattern
        boolean tail = p.endsWith('/**')
        if (tail) p = p.substring(0, p.length() - 3)
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i)
            if (c == '*' as char && i + 1 < p.length() && p.charAt(i + 1) == '*' as char) {
                sb << '.*'
                i++
            } else if (c == '*' as char) {
                sb << '[^/]*'
            } else if (c == '?' as char) {
                sb << '[^/]'
            } else {
                sb << Pattern.quote(c.toString())
            }
        }
        if (tail) sb << '(/.*)?'
        sb.toString()
    }

    @Override
    String toString() {
        "${httpMethod ?: '*'} ${pattern} -> ${access}"
    }
}
