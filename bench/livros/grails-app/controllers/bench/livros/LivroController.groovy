package bench.livros

class LivroController {
    LivroService livroService

    def index() {
        [livros: livroService.todos()]
    }
}
