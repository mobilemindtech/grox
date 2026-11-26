package extra

/** {@code <nota:total/>}: TagLib da biblioteca, com mensagem do messages.properties dela. */
class NotaTagLib {

    static namespace = 'nota'

    NotaService notaService

    def total = { attrs ->
        out << g.message(code: 'nota.total', args: [notaService.total()])
    }
}
