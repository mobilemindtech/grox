package org.grox.security

/** Usuários de {@code grails.plugin.springsecurity.users} (extensão Grox, útil em dev e testes). */
class InMemoryUserDetailsService implements UserDetailsService {

    private final Map<String, GroxUser> users = [:]

    InMemoryUserDetailsService(List<Map> definitions) {
        definitions?.each { Map d ->
            if (!d.username || d.password == null) throw new IllegalArgumentException("usuário em memória sem username/password: ${d}")
            def u = new GroxUser(username: d.username.toString(), password: d.password.toString(),
                    enabled: d.enabled != false, tenantId: (Serializable) d.tenantId)
            u.roles = ((d.roles ?: []) as List)*.toString()
            users[u.username] = u
        }
    }

    @Override
    GroxUser loadUserByUsername(String username) {
        users[username]
    }

    boolean isEmpty() {
        users.isEmpty()
    }
}
