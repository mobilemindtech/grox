package org.grox.testjobs

/** Job de teste: dois triggers; triggers() devolve o último, como no plugin quartz. */
class RelatorioJob {
    static triggers = {
        simple name: 'aquecimento', repeatInterval: 60000L
        cron name: 'relatorio', cronExpression: '0 30 5 ? * *'
    }

    def summary = 'Gera o relatório diário'

    def execute() {}
}
