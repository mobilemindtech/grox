package org.grox.dsl

/**
 * Classe de rotas por closure, descoberta no pacote da aplicação. O closure de {@link #getRoutes} roda
 * com um {@link RouteBuilder} como delegate; cada handler roda com o {@link RouteContext} da requisição
 * como delegate e a própria classe como owner (services injetados pelo nome).
 * <pre>
 * class Rotas implements Routes {
 *     LivroService livroService
 *     Closure routes = {
 *         get('/livros') { render view: 'livros/index', model: [livros: livroService.todos()] }
 *         post('/livros') { redirect url: "/livros/${livroService.salvar(params).id}" }
 *         group('/relatorios', execution: 'actor', poolSize: 2) {
 *             get('/vendas') { render json: livroService.vendas() }
 *         }
 *     }
 * }
 * </pre>
 */
interface Routes {
    Closure getRoutes()
}
