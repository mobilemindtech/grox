package sample

import grails.plugins.orm.auditable.Auditable

/** Domain auditada pelo plugin audit-logging (grails.plugin.auditLog no application.yml). */
class Contrato implements Auditable {
    String numero
    BigDecimal valor
    String senha          // mascarada no audit log (mask padrão: password; aqui, senha)

    static constraints = {
        senha nullable: true
    }
}
