package bench.livros

class OlaController {
    def index(String nome) {
        render text: "Olá, ${nome}!", contentType: 'text/plain'
    }
}
