package sample.plain

import org.grox.dsl.Routes

/** Rotas por closure: páginas HTML (GSP, Pug e Markdown) e um relatório em atores. */
class Rotas implements Routes {

    LivroService livroService

    Closure routes = {
        get('/') { redirect '/livros' }
        // texto puro: a rota mais barata, a mesma do benchmark Grails x Grox (bench/livros, docs/benchmark.md)
        get('/ola/{nome}', access: 'permitAll') { render text: "Olá, ${params.nome}!", contentType: 'text/plain' }
        get('/livros', name: 'index') {
            render view: 'livros/index', model: [livros: livroService.todos()]
        }
        // sem render: o Map devolvido vira o modelo da view da rota
        get('/livros/{id}', name: 'show', view: 'livros/show') {
            Livro livro = livroService.buscar(params.long('id'))
            if (!livro) return render(status: 404)
            [livro: livro]
        }
        post('/livros', name: 'save') {
            Livro livro = livroService.salvar(params)
            if (livro.hasErrors()) {
                return render(view: 'livros/index', model: [livros: livroService.todos(), novo: livro], status: 422)
            }
            flash.message = "Livro ${livro.titulo} criado"
            redirect "/livros/${livro.id}"
        }
        // DSL de HTML com o layout da app: as tags em g.*, texto escapado
        get('/sobre', name: 'sobre') {
            render(layout: 'main') {
                h1 'Sobre'
                p "${livroService.todos().size()} livros & contando"
                a(href: g.createLink(controller: 'rotas', action: 'index'), 'Ver livros')
            }
        }
        // a mesma lista em Pug (TagLibs como elementos: g:link, g:form, fmt:autor) e uma página em Markdown
        get('/catalogo', name: 'catalogo') {
            render view: 'catalogo/index', model: [livros: livroService.todos()]
        }
        get('/ajuda', name: 'ajuda') {
            render view: 'paginas/ajuda', model: [total: livroService.todos().size()]
        }
        group('/relatorios', execution: 'actor', poolSize: 2) {
            // vale só para as rotas deste grupo
            after { response.setHeader('X-Relatorio', actionName) }
            get('/autores', name: 'autores') {
                render json: [execucao: route.execution, autores: livroService.porAutor()]
            }
        }
        // ns é o mesmo que group; sem caminho, a rota atende o caminho do grupo
        ns('/notas', access: 'permitAll') {
            get(name: 'notas') { render text: 'notas' }
            get('{id}', name: 'nota') { render text: "nota ${params.id}" }
        }
    }
}
