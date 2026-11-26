package sample

import grails.converters.JSON

import static grails.async.Promises.task
import static grails.async.Promises.tasks

/**
 * Actions assíncronas (grails.async). poolSize = 1: um único ator; a versão síncrona
 * enfileira as requisições, a assíncrona libera o ator logo após devolver a promise.
 */
class AsyncController {

    static poolSize = 1

    UsuarioService usuarioService

    /** Síncrona: segura o único ator durante a espera. */
    def espera(Integer ms) {
        Thread.sleep(ms ?: 300)
        render text: 'ok', contentType: 'text/plain'
    }

    /** Assíncrona: o ator fica livre; a resposta sai quando a tarefa termina. */
    def esperaAsync(Integer ms) {
        task {
            Thread.sleep(ms ?: 300)
            render text: 'ok', contentType: 'text/plain'
        }
    }

    /** Map devolvido pela tarefa → view async/relatorio.gsp (com layout e interceptors). */
    def relatorio() {
        task {
            [total: usuarioService.contar(), usuario: principal?.username, thread: Thread.currentThread().virtual]
        }
    }

    /** Tarefas em paralelo e respond no fim. */
    def painel() {
        tasks(usuarios: { Usuario.count() }, tarefas: { Tarefa.count() }, tenant: { grails.gorm.multitenancy.Tenants.currentIdOrNull() })
                .then { Map m -> respond m }
    }

    def falhaAsync() {
        task { throw new IllegalStateException('falhou na tarefa') }
    }

    def lentaDemais() {
        task { Thread.sleep(5000); render 'tarde demais' }
    }
}
