package org.grox.security

import groovy.transform.CompileStatic

/**
 * Entrada do {@code filterChain.chainMap}: padrão Ant e a lista de filtros do Spring Security
 * ({@code 'none'}, {@code 'JOINED_FILTERS,-x,-y'} ou nomes explícitos). Como no Spring, vale a
 * primeira entrada que casa. O Grox não tem os filtros em si; cada um vira uma etapa da cadeia:
 * <ul>
 *   <li>{@code securityContextPersistenceFilter}: autenticação da sessão;</li>
 *   <li>{@code restTokenValidationFilter}: token REST;</li>
 *   <li>{@code basicAuthenticationFilter}: HTTP Basic;</li>
 *   <li>{@code anonymousAuthenticationFilter}: sem ele, quem não se autenticou recebe 401 (ou vai
 *       para o login) mesmo em regra {@code permitAll}, como o {@code FilterSecurityInterceptor};</li>
 *   <li>{@code filterInvocationInterceptor}: sem ele, não há autorização;</li>
 *   <li>{@code exceptionTranslationFilter}: navegador sem login vai para a página de login;
 *       sem ele (só o {@code restExceptionTranslationFilter}), 401.</li>
 * </ul>
 */
@CompileStatic
class FilterChainRule {

    static final String SESSION = 'securityContextPersistenceFilter'
    static final String TOKEN = 'restTokenValidationFilter'
    static final String BASIC = 'basicAuthenticationFilter'
    static final String ANONYMOUS = 'anonymousAuthenticationFilter'
    static final String AUTHORIZE = 'filterInvocationInterceptor'
    static final String EXCEPTION_TRANSLATION = 'exceptionTranslationFilter'

    /** Filtros de {@code JOINED_FILTERS} que o Grox distingue. */
    static final List<String> JOINED = [SESSION, TOKEN, BASIC, ANONYMOUS, AUTHORIZE, EXCEPTION_TRANSLATION]

    /** Cadeia padrão (sem chainMap ou sem entrada que case). */
    static final FilterChainRule DEFAULT = new FilterChainRule('/**', 'JOINED_FILTERS')

    final String pattern
    final AccessRule rule
    final boolean none
    final Set<String> filters

    FilterChainRule(String pattern, String filters) {
        this.pattern = pattern
        this.rule = new AccessRule(pattern, null, ['permitAll'])
        String spec = filters?.trim() ?: 'JOINED_FILTERS'
        this.none = spec == 'none'
        Set<String> set = new LinkedHashSet<>()
        if (!none) {
            for (String part : spec.split(',')) {
                String name = part.trim()
                if (!name) continue
                if (name == 'JOINED_FILTERS') set.addAll(JOINED)
                else if (name.startsWith('-')) set.remove(name.substring(1).trim())
                else set.add(name)
            }
        }
        this.filters = set.asImmutable()
    }

    boolean matches(String path) {
        rule.regex.matcher(path).matches()
    }

    boolean getSession() { filters.contains(SESSION) }

    boolean getToken() { filters.contains(TOKEN) }

    boolean getBasic() { filters.contains(BASIC) }

    boolean getAnonymous() { filters.contains(ANONYMOUS) }

    boolean getAuthorize() { filters.contains(AUTHORIZE) }

    /** Sem login: redireciona o navegador (true) ou responde 401 (false). */
    boolean getRedirectToLogin() { filters.contains(EXCEPTION_TRANSLATION) }

    /** @return a primeira entrada que casa com o caminho, ou {@link #DEFAULT} */
    static FilterChainRule find(List<FilterChainRule> chains, String path) {
        for (FilterChainRule c : chains) {
            if (c.matches(path)) return c
        }
        DEFAULT
    }

    static List<FilterChainRule> parse(Object chainMap) {
        if (!(chainMap instanceof List)) return []
        List<FilterChainRule> out = []
        for (Object o : (List) chainMap) {
            if (o instanceof Map && ((Map) o).pattern) {
                Map m = (Map) o
                out << new FilterChainRule(m.pattern.toString(), m.filters?.toString())
            }
        }
        out
    }

    @Override
    String toString() { "${pattern} -> ${none ? 'none' : filters.join(',')}" }
}
