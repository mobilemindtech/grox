package sample

import grails.gorm.multitenancy.Tenants
import sample.auth.Role
import sample.auth.User
import sample.auth.UserRole

class BootStrap {

    def init = { servletContext ->
        // segurança (todos os ambientes; as senhas são codificadas no beforeInsert)
        def admin = new Role(authority: 'ROLE_ADMIN').save(failOnError: true)
        def user = new Role(authority: 'ROLE_USER').save(failOnError: true)
        UserRole.create(new User(username: 'admin', password: 'admin', tenantId: 'acme').save(failOnError: true), admin)
        UserRole.create(new User(username: 'maria', password: 'maria', tenantId: 'acme').save(failOnError: true), user)
        UserRole.create(new User(username: 'joao', password: 'joao', tenantId: 'globex').save(failOnError: true), user)
        UserRole.create(new User(username: 'inativo', password: 'inativo', tenantId: 'acme', enabled: false).save(failOnError: true), user)
        Tenants.withId('acme') { new Tarefa(titulo: 'Revisar contrato').save(failOnError: true) }
        Tenants.withId('globex') { new Tarefa(titulo: 'Planejar sprint').save(failOnError: true) }

        environments {
            development {
                def dev = new Grupo(nome: 'Desenvolvimento').save(failOnError: true)
                new Grupo(nome: 'Operações').save(failOnError: true)
                new Usuario(nome: 'Ana', email: 'ana@exemplo.com', idade: 30, grupo: dev).save(failOnError: true)
                new Usuario(nome: 'Bruno', email: 'bruno@exemplo.com', idade: 17).save(failOnError: true)
            }
        }
    }

    def destroy = {
    }
}
