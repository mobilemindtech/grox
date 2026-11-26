package sample.plain

import org.grox.dsl.Get
import org.grox.dsl.Handler
import org.grox.dsl.OnError
import org.grox.dsl.Post

/** Rotas por anotação: API JSON com respond, no estilo dos controllers do Grails. */
@Handler('/api/livros')
class LivrosApi {

    LivroService livroService

    @Get
    def index() {
        respond livroService.todos()
    }

    @Get('/{id}')
    def show(Long id) {
        respond livroService.buscar(id)
    }

    @Post
    def save() {
        Livro livro = livroService.salvar(request.json as Map)
        if (livro.hasErrors()) return respond(livro.errors, status: 422)
        respond livro, status: 201
    }

    /** Exceção numa rota da classe (como um JSON inválido no corpo): 400 com a mensagem. */
    @OnError
    def falhou(Throwable e) {
        render status: 400, json: [erro: e.message ?: e.getClass().simpleName]
    }
}
