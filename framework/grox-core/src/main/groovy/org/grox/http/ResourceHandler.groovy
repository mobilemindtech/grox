package org.grox.http

/** Arquivos servidos pelo caminho, antes das rotas: os estáticos ({@link StaticResources}) e os assets. */
interface ResourceHandler {
    /** Nome nas métricas por rota ({@code static}, {@code assets}). */
    String getName()

    boolean handles(GroxRequest req)

    /** O arquivo, ou null (404). Chamado numa thread que pode bloquear. */
    GroxResponse serve(GroxRequest req)
}
