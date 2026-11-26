package sample.plain

/** Como o BootStrap do Grails, sem o módulo Grails: descoberto no pacote da aplicação, com services injetados. */
class BootStrap {

    LivroService livroService

    def init = { servletContext ->
        environments {
            test { livroService.salvar(titulo: 'Senhora', autor: 'Alencar', ano: 1875) }
        }
    }

    def destroy = {
    }
}
