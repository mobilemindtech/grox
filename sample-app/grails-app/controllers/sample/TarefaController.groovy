package sample

class TarefaController {

    static responseFormats = ['html', 'json']
    static allowedMethods = [save: 'POST', delete: ['DELETE', 'POST']]

    TarefaService tarefaService

    def index() {
        respond Tarefa.list(), model: [tarefa: new Tarefa()]
    }

    def show(Tarefa tarefa) {
        respond tarefa
    }

    def save(Tarefa tarefa) {
        if (tarefa.hasErrors() || !tarefa.save()) {
            withFormat {
                html { render view: 'index', model: [tarefaList: Tarefa.list(), tarefa: tarefa], status: 422 }
                json { respond tarefa.errors, status: 422 }
            }
            return
        }
        withFormat {
            html { redirect action: 'index' }
            json { respond tarefa, status: 201 }
        }
    }

    def delete(Tarefa tarefa) {
        if (tarefa == null) {
            render status: 404
            return
        }
        tarefa.delete()
        withFormat {
            html { redirect action: 'index' }
            json { render status: 204 }
        }
    }

    def resumo() {
        respond([tenant: tarefaService.tenantAtual(), pendentes: tarefaService.pendentes()*.titulo, totalGeral: tarefaService.totalGeral()])
    }
}
