package org.grox.routing

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse

import java.util.concurrent.CompletableFuture

/**
 * Quem atende as rotas no {@link org.grox.http.HttpPipeline}: os controllers do módulo Grails, as rotas
 * por closure ou anotação do {@code grox-dsl}. O pipeline cuida do resto (segurança, recursos,
 * endpoints, sessão, erros).
 */
interface Router {
    /** A rota do caminho e do método, ou null. Sem IO: pode rodar no event loop. */
    RouteMatch match(GroxRequest req)

    /** Atende a rota casada. Exceção ou {@code errorStatus} na resposta viram a página de erro. */
    CompletableFuture<GroxResponse> handle(GroxRequest req, RouteMatch match)

    /** Página de erro própria do status (como o {@code "404"} do UrlMappings), ou null para a padrão. */
    CompletableFuture<GroxResponse> errorPage(GroxRequest req, int status)

    /** Nome da rota nas métricas ({@code livro.show}), ou null. */
    String routeKey(RouteMatch match)

    /** Há um destino com esse nome (a segurança pergunta pelos controllers {@code login}/{@code logout}). */
    boolean hasTarget(String name)
}
