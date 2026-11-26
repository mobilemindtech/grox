package org.grox.security

import org.grox.http.GroxSession

/**
 * Eventos do {@link SecurityFilter} para outras APIs (o plugin spring-security guarda a exceção
 * do login na sessão e publica o {@code RestTokenCreationEvent}). Os métodos têm implementação vazia.
 */
interface SecurityListener {

    /** Login por formulário recusado; {@code error} é o código da mensagem ({@code springSecurity.errors.login.*}). */
    default void loginFailed(GroxSession session, String error, String username) {}

    /** Login por formulário aceito, antes de gravar a autenticação na sessão. */
    default void loginSucceeded(GroxSession session) {}

    /** Token REST emitido; roda com o usuário já autenticado ({@link SecurityContextHolder}). */
    default void tokenIssued(GroxUser user, String accessToken, String refreshToken, Integer expiration) {}
}
