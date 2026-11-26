package sample

import grails.gorm.MultiTenant

/** Multi-tenant (DISCRIMINATOR): cada empresa só vê as próprias tarefas. */
class Tarefa implements MultiTenant<Tarefa> {
    String titulo
    boolean concluida
    Date dateCreated
    String tenantId

    static constraints = {
        titulo blank: false, size: 1..200
    }

    static mapping = {
        sort 'titulo'
    }
}
