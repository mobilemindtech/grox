package sample

import grails.converters.JSON

class ErroController {

    def interno() {
        def ex = request.getAttribute('exception')
        withFormat {
            html { render status: 500, text: "<h1>Erro interno</h1><p>${ex?.message}</p>" }
            json { render(status: 500, contentType: 'application/json', text: [erro: ex?.message ?: 'erro interno'] as JSON) }
        }
    }

    def proibido() {
        withFormat {
            html { render status: 403, text: "<h1>${message(code: 'springSecurity.denied.message')}</h1>" }
            json { render(status: 403, contentType: 'application/json', text: [erro: 'acesso negado'] as JSON) }
        }
    }

    def naoEncontrado() {
        withFormat {
            html { render status: 404, text: "<h1>Página não encontrada</h1><p>${request.forwardURI}</p>" }
            json { render(status: 404, contentType: 'application/json', text: [erro: 'não encontrado', path: request.forwardURI, status: request['javax.servlet.error.status_code']] as JSON) }
        }
    }
}
