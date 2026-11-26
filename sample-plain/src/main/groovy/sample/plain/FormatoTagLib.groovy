package sample.plain

/** TagLib da aplicação, usada nas views GSP, Pug ({@code fmt:autor(livro=l)}) e Markdown. */
class FormatoTagLib implements org.grox.view.taglib.TagLibApi {
    static namespace = 'fmt'

    def autor = { attrs ->
        out << '<span class="autor">' << attrs.livro.autor.toString().encodeAsHTML() << '</span>'
    }

    /** Destaque com o corpo da tag. */
    def destaque = { attrs, body ->
        out << '<strong class="destaque">' << body() << '</strong>'
    }
}
