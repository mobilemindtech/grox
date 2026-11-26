package sample

import grails.web.servlet.mvc.GrailsParameterMap
import jakarta.annotation.PostConstruct

/** Service comum (singleton, sem ator). */
class SaudacaoService {

    UsuarioService usuarioService
    /** Preenchido no @PostConstruct, quando as dependências já estão injetadas. */
    boolean iniciado

    @PostConstruct
    void iniciar() {
        iniciado = usuarioService != null
    }

    String saudar(String nome) {
        "Olá, ${nome}!"
    }

    /** params do controller com o tipo do Grails. */
    String saudarPorParams(GrailsParameterMap params) {
        saudar(params.nome ?: 'mundo')
    }
}
