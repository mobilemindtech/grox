package org.grox.security

/**
 * Expressões de acesso do Spring Security usadas no Grails: {@code permitAll}, {@code denyAll},
 * {@code isAuthenticated()}, {@code isFullyAuthenticated()}, {@code isAnonymous()},
 * {@code ROLE_X}, {@code hasRole('X')}, {@code hasAnyRole('A','B')}, {@code hasAuthority},
 * {@code hasAnyAuthority}, {@code IS_AUTHENTICATED_*}, com {@code and}, {@code or}, {@code !}.
 */
class AccessExpression {

    /** Uma lista de acesso concede se qualquer item conceder (como no Grails). */
    static boolean anyGranted(Collection<String> access, Authentication auth) {
        access.any { evaluate(it, auth) }
    }

    private static final Map<String, Closure<Boolean>> COMPILED = new java.util.concurrent.ConcurrentHashMap<>()

    /** A expressão é compilada uma vez em um predicado sobre a autenticação. */
    static boolean evaluate(String expression, Authentication auth) {
        compile(expression).call(auth)
    }

    static Closure<Boolean> compile(String expression) {
        Closure<Boolean> c = COMPILED.get(expression)
        if (c == null) {
            c = build(expression)
            COMPILED.put(expression, c)
        }
        c
    }

    private static Closure<Boolean> build(String expression) {
        String e = expression.trim()
        if (e.startsWith('(') && e.endsWith(')') && balanced(e.substring(1, e.length() - 1))) return build(e.substring(1, e.length() - 1))
        List<String> ors = splitTop(e, 'or')
        if (ors.size() > 1) {
            List<Closure<Boolean>> parts = ors.collect { build(it) }
            return { Authentication a -> parts.any { it.call(a) } }
        }
        List<String> ands = splitTop(e, 'and')
        if (ands.size() > 1) {
            List<Closure<Boolean>> parts = ands.collect { build(it) }
            return { Authentication a -> parts.every { it.call(a) } }
        }
        if (e.startsWith('!') || e.startsWith('not ')) {
            Closure<Boolean> inner = build(e.startsWith('!') ? e.substring(1) : e.substring(4))
            return { Authentication a -> !inner.call(a) }
        }
        if (e in ['permitAll', 'IS_AUTHENTICATED_ANONYMOUSLY']) return { Authentication a -> true }
        if (e == 'denyAll') return { Authentication a -> false }
        if (e in ['isAuthenticated()', 'isFullyAuthenticated()', 'IS_AUTHENTICATED_FULLY', 'IS_AUTHENTICATED_REMEMBERED', 'isRememberMe()']) return { Authentication a -> a != null }
        if (e == 'isAnonymous()') return { Authentication a -> a == null }
        def m = e =~ /^(hasRole|hasAnyRole|hasAuthority|hasAnyAuthority)\((.*)\)$/
        if (m.matches()) {
            List<String> args = (m.group(2) =~ /'([^']*)'|"([^"]*)"/).collect { List g -> (g[1] ?: g[2]) as String }
            if (!args) throw new IllegalArgumentException("Expressão de acesso sem argumentos: ${e}")
            boolean roles = m.group(1).contains('Role')
            List<String> wanted = args.collect { String a -> roles && !a.startsWith('ROLE_') ? 'ROLE_' + a : a }
            return { Authentication a -> a != null && wanted.any { a.hasAuthority(it) } }
        }
        if (e ==~ /[A-Z][A-Z0-9_]*/) return { Authentication a -> a != null && a.hasAuthority(e) }
        throw new IllegalArgumentException("Expressão de acesso inválida: ${expression}")
    }

    /** Valida na subida (erros de configuração aparecem cedo). */
    static void validate(String expression) {
        compile(expression)
    }

    private static List<String> splitTop(String e, String op) {
        List<String> parts = []
        int depth = 0
        int start = 0
        String token = " ${op} "
        for (int i = 0; i < e.length(); i++) {
            char c = e.charAt(i)
            if (c == '(' as char) depth++
            else if (c == ')' as char) depth--
            else if (depth == 0 && e.startsWith(token, i)) {
                parts << e.substring(start, i)
                start = i + token.length()
                i = start - 1
            }
        }
        parts << e.substring(start)
        parts
    }

    private static boolean balanced(String s) {
        int depth = 0
        for (char c : s.toCharArray()) {
            if (c == '(' as char) depth++
            else if (c == ')' as char && --depth < 0) return false
        }
        depth == 0
    }
}
