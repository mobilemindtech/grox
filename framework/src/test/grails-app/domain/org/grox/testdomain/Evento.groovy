package org.grox.testdomain

/** Registra os eventos de domain (teste do DomainEventBridge). */
class Evento {
    String nome
    String marca
    Date dateCreated
    Date lastUpdated

    static List<String> chamadas = Collections.synchronizedList([])

    static constraints = {
        marca nullable: true
    }

    def beforeValidate() { chamadas << 'beforeValidate' }

    def beforeInsert() { chamadas << 'beforeInsert' }

    def beforeUpdate() {
        chamadas << 'beforeUpdate'
        marca = 'alterado em beforeUpdate'   // alteração feita no evento: precisa ir para o SQL
    }

    def afterInsert() { chamadas << 'afterInsert' }

    def afterUpdate() { chamadas << 'afterUpdate' }

    def afterDelete() { chamadas << 'afterDelete' }

    def afterLoad() { chamadas << 'afterLoad' }
}
