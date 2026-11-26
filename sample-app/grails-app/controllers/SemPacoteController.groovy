/** Controller no pacote padrão (sem package), como o LoginController de apps Grails antigas. */
class SemPacoteController {
    def index() {
        render text: 'sem pacote', contentType: 'text/plain'
    }

    /** {@code render text: null} envia "null", como no Grails. */
    def nulo() {
        response.contentType = 'application/json'
        render text: null
    }

    /** Dois renders de JSON (o JsonResponse do app C faz isso): só o primeiro vale, como no Grails. */
    def jsonDuplo() {
        render([a: 1] as grails.converters.JSON)
        render([a: 1] as grails.converters.JSON)
    }
}
