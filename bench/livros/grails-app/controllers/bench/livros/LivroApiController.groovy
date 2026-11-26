package bench.livros

class LivroApiController {
    static responseFormats = ['json']

    LivroService livroService

    def show(Long id) {
        Livro livro = livroService.buscar(id)
        if (!livro) return render(status: 404)
        respond livro
    }
}
