package org.grox.http

import org.grox.routing.RouteMatch

/** Filtro antes das rotas (a segurança): decide se a requisição segue ou já tem resposta. */
interface RequestFilter {
    /**
     * Requisições que dispensam o filtro, decididas no event loop sem IO (assets, rotas liberadas).
     * @param resource o recurso que atende o caminho (estáticos, assets), ou null
     */
    boolean bypasses(GroxRequest req, ResourceHandler resource)

    /**
     * Chamado numa thread que pode bloquear (sessão, banco, bcrypt).
     * @param match a rota casada, ou null (recurso, endpoint ou caminho sem rota)
     * @return null para seguir; senão a resposta (com {@code errorStatus}, vira a página de erro)
     */
    GroxResponse process(GroxRequest req, RouteMatch match)

    /** Caminhos do próprio filtro (login, logout), para as métricas por rota. */
    boolean ownsPath(String path)
}
