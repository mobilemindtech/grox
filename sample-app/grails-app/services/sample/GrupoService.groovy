package sample

import grails.gorm.transactions.Transactional
import org.springframework.transaction.annotation.Propagation

/** Transações declarativas usadas pelo TransacaoController (testes de transação em actions). */
@Transactional
class GrupoService {

    /** Grava e falha: a transação do método volta atrás. */
    Grupo criarEFalhar(String nome) {
        new Grupo(nome: nome).save(failOnError: true, flush: true)
        throw new IllegalStateException('falha no service')
    }

    /** Sessão e transação próprias: fica gravado mesmo se quem chamou voltar atrás. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Grupo criarIsolado(String nome) {
        new Grupo(nome: nome).save(failOnError: true, flush: true)
    }
}
