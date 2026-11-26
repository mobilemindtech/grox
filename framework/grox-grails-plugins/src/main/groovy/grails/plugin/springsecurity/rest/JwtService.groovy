package grails.plugin.springsecurity.rest

import org.grox.security.Jwt

/**
 * Compatibilidade com o spring-security-rest: acesso ao JWT do Grox
 * (bean {@code jwtService}, disponível quando {@code grox.security.jwt} está ativo).
 */
class JwtService {
    Jwt jwt

    Map parse(String token) {
        jwt?.verify(token)
    }
}
