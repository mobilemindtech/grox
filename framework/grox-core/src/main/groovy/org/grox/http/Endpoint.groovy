package org.grox.http

/**
 * Endpoint especial atendido antes das rotas (o {@code /actuator} do management, o console web):
 * casa pelo caminho, declara as regras de acesso que a segurança aplica e responde fora do event loop.
 */
interface Endpoint {
    /** Nome nas métricas por rota ({@code management}, {@code console}). */
    String getName()

    boolean handles(String path)

    /** Regras de acesso do caminho, no formato do {@code interceptUrlMap} ({@code permitAll}, {@code ROLE_ADMIN}). */
    List<String> accessFor(String path)

    /** Sem a segurança ativa, se a requisição pode ser atendida (senão 403). */
    boolean allowedWithoutSecurity(GroxRequest req)

    /** Chamado numa thread que pode bloquear. {@code errorStatus} na resposta vira a página de erro. */
    GroxResponse handle(GroxRequest req)
}
