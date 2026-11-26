package org.grox.dsl

/**
 * Bloco {@code security { }} da DSL de projeto. {@code user} e {@code rule} acrescentam usuários e regras de URL
 * (depois das dos arquivos de configuração); as demais atribuições vão para {@code grails.plugin.springsecurity}
 * ({@code useBasicAuth = true}, {@code rest.active = true}), como valores padrão.
 */
class SecurityBlock extends ConfigBlock {

    final List<Map> users
    final List<Map> rules

    SecurityBlock(Map<String, Object> springSecurity, List<Map> users, List<Map> rules) {
        super(springSecurity)
        this.users = users
        this.rules = rules
    }

    /** {@code user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']}. */
    void user(Map attrs, String username) {
        users << ([username: username] + attrs)
    }

    /** {@code rule '/api/**', 'ROLE_ADMIN'} ou {@code rule '/x', httpMethod: 'GET', 'permitAll', 'isAuthenticated()'}. */
    void rule(String pattern, String... access) {
        rule([:], pattern, access)
    }

    void rule(Map attrs, String pattern, String... access) {
        rules << ([pattern: pattern, access: access.toList()] + attrs)
    }

    @Override
    Object invokeMethod(String name, Object args) {
        Object[] a = (Object[]) args
        if (name == 'user') return a[0] instanceof Map ? user((Map) a[0], (String) a[1]) : user([:], (String) a[0])
        if (name == 'rule') {
            if (a[0] instanceof Map) return rule((Map) a[0], (String) a[1], a.drop(2).collect { it.toString() } as String[])
            return rule((String) a[0], a.drop(1).collect { it.toString() } as String[])
        }
        super.invokeMethod(name, args)
    }
}
