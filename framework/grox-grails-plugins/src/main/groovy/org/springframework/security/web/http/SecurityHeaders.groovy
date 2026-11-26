package org.springframework.security.web.http

/**
 * Compatibilidade com o Spring Security: só para o import resolver (o app C importa a classe sem usá-la).
 * O {@code bearerToken} do Spring devolve um {@code Consumer<HttpHeaders>}; aqui o consumidor recebe o
 * mapa de cabeçalhos da requisição.
 */
final class SecurityHeaders {

    private SecurityHeaders() {
    }

    /** Cabeçalho {@code Authorization: Bearer <token>}. */
    static java.util.function.Consumer<Map<String, Object>> bearerToken(String token) {
        { Map<String, Object> headers -> headers.put('Authorization', 'Bearer ' + token) } as java.util.function.Consumer<Map<String, Object>>
    }
}
