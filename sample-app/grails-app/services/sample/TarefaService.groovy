package sample

import grails.gorm.multitenancy.Tenants
import grails.gorm.multitenancy.WithoutTenant
import grails.gorm.transactions.Transactional

/**
 * Não precisa de anotação de tenant: as consultas de Tarefa (MultiTenant) já são filtradas
 * pelo tenant do usuário logado. Só o que foge da regra é anotado (@WithoutTenant).
 */
@Transactional
class TarefaService {

    List<Tarefa> pendentes() {
        Tarefa.findAllByConcluida(false)
    }

    Serializable tenantAtual() {
        Tenants.currentId()
    }

    /** Total de todas as empresas (sem filtro de tenant). */
    @WithoutTenant
    long totalGeral() {
        Tarefa.count()
    }
}
