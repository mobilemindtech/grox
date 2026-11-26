package sample

/** Job de exemplo (plugin quartz): agendado de madrugada; a tela de jobs pode executá-lo à mão pelo bean. */
class ResumoJob {
    static triggers = {
        cron name: 'resumoDiario', cronExpression: '0 0 3 * * ?'
    }
    static concurrent = false

    def usuarioService
    long ultimoTotal = -1

    def execute() {
        ultimoTotal = usuarioService.contar()
    }
}
