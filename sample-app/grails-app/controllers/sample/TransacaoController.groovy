package sample

/** Transações dentro de actions: withTransaction, rollbackOnly, withNewTransaction e services transacionais. */
class TransacaoController {

    GrupoService grupoService

    private static void gravar(String nome) {
        new Grupo(nome: nome).save(failOnError: true, flush: true)
    }

    /** Exceção dentro do withTransaction: as duas gravações voltam e a action responde 500. */
    def falha(String nome) {
        Grupo.withTransaction {
            gravar("${nome}-1")
            gravar("${nome}-2")
            throw new IllegalStateException('falha na action')
        }
    }

    def rollbackOnly(String nome) {
        Grupo.withTransaction { status ->
            gravar(nome)
            status.setRollbackOnly()
        }
        render text: 'ok', contentType: 'text/plain'
    }

    /** withNewTransaction dentro de uma transação que volta: só o de dentro fica. */
    def novaTransacao(String nome) {
        try {
            Grupo.withTransaction {
                gravar("${nome}-fora")
                Grupo.withNewTransaction { gravar("${nome}-dentro") }
                throw new IllegalStateException('volta a de fora')
            }
        } catch (IllegalStateException ignored) {
        }
        render text: 'ok', contentType: 'text/plain'
    }

    def serviceFalha(String nome) {
        try {
            grupoService.criarEFalhar(nome)
        } catch (IllegalStateException ignored) {
        }
        render text: 'ok', contentType: 'text/plain'
    }

    /** @Transactional(REQUIRES_NEW) chamado de uma transação da action que volta atrás. */
    def requiresNew(String nome) {
        try {
            Grupo.withTransaction {
                grupoService.criarIsolado("${nome}-isolado")
                gravar("${nome}-fora")
                throw new IllegalStateException('volta a de fora')
            }
        } catch (IllegalStateException ignored) {
        }
        render text: 'ok', contentType: 'text/plain'
    }

    /** Leitura do que foi gravado na mesma transação e participação de uma transação aninhada. */
    def aninhada(String nome) {
        def total = Grupo.withTransaction {
            gravar("${nome}-a")
            Grupo.withTransaction { gravar("${nome}-b") }   // participa da de fora
            Grupo.countByNomeLike("${nome}-%")
        }
        render text: "${total}", contentType: 'text/plain'
    }

    def criar(String nome) {
        Grupo.withTransaction { gravar(nome) }
        render text: 'ok', contentType: 'text/plain'
    }
}
