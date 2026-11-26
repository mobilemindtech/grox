package sample

/** TagLib da aplicação: {@code <fmt:idade valor="${n}"/>}, {@code <fmt:badge>..</fmt:badge>}. */
class FormatacaoTagLib {

    static namespace = 'fmt'

    def idade = { attrs ->
        if (attrs.valor == null) {
            out << '—'
            return
        }
        int n = attrs.valor as int
        out << g.message(code: n < 18 ? 'usuario.menor' : 'usuario.anos', args: [n])
    }

    /** Usa o {@code grailsAttributes} do Grails (como o PaginatorTagLib do piloto). */
    def rota = { attrs ->
        def messageSource = grailsAttributes.messageSource
        out << grailsAttributes.getControllerActionUri(request) << ' ' << messageSource.getMessage('usuario.anos', [30] as Object[], Locale.forLanguageTag('pt-BR'))
    }

    /** Outra TagLib pelo namespace como propriedade ({@code fmt.idade}), como no Grails. */
    def rotulo = { attrs ->
        out << 'Idade: ' << fmt.idade(valor: attrs.valor)
    }

    /** Lê uma coleção lazy ({@code usuarios}): precisa da sessão de dados aberta durante a view (também no login). */
    def grupos = { attrs ->
        out << Grupo.list(sort: 'nome').collect { "${it.nome}: ${it.usuarios ? it.usuarios.size() : 0}" }.join(', ')
    }

    def badge = { attrs, body ->
        out << '<span class="badge">' << body() << '</span>'
    }
}
