package org.grox

import grails.gorm.multitenancy.Tenants
import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.GormEvents
import org.grox.data.tenant.TenantSupport
import org.grox.plugins.auditlog.AuditLogListener
import org.grox.plugins.auditlog.AuditLogPlugin
import org.grox.testdomain.AuditoriaTenant
import org.grox.testdomain.NotaAuditada
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Audit log com domain de auditoria multi-tenant: o registro vai para o tenant da entidade auditada. */
class AuditLogTenantTest {

    @BeforeEach
    void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:auditTenant;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop']])
        def datastore = new Datastore(config, [NotaAuditada, AuditoriaTenant], AuditLogTenantTest.classLoader)
        Gorm.init(datastore)
        TenantSupport.configure(new SystemPropertyTenantResolver(), datastore.tenantType)
    }

    @AfterEach
    void stop() {
        AuditLogPlugin.shutdown()
        GormEvents.reset()
        Gorm.reset()
        TenantSupport.reset()
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY)
    }

    private static void ativar(boolean async) {
        AuditLogPlugin.init([auditDomainClassName: AuditoriaTenant.name, asyncWrite: async], AuditLogTenantTest.classLoader, null)
        GormEvents.register(new AuditLogListener())
    }

    private static Map<String, List<String>> registros() {
        Tenants.withoutId { AuditoriaTenant.list().groupBy { it.tenantId }.collectEntries { k, v -> [k, v*.eventName.unique().sort()] } }
    }

    @Test
    void 'gravacao assincrona e dentro de withoutId usam o tenant da entidade'() {
        ativar(true)
        System.setProperty(SystemPropertyTenantResolver.PROPERTY, 'acme')
        Long id = NotaAuditada.withTransaction { new NotaAuditada(texto: 'a').save(failOnError: true).id }
        // um job sem tenant (withoutId) altera a nota: antes, o registro ficava sem tenant e não era gravado
        System.clearProperty(SystemPropertyTenantResolver.PROPERTY)
        Tenants.withoutId {
            NotaAuditada.withTransaction {
                def n = NotaAuditada.get(id)
                n.texto = 'b'
                n.save(failOnError: true)
            }
        }
        AuditLogPlugin.shutdown()   // espera as gravações assíncronas (antes, a thread do executor não tinha tenant)
        assert registros() == [acme: ['INSERT', 'UPDATE']]
    }

    @Test
    void 'gravacao sincrona tambem'() {
        ativar(false)
        System.setProperty(SystemPropertyTenantResolver.PROPERTY, 'globex')
        NotaAuditada.withTransaction { new NotaAuditada(texto: 'x').save(failOnError: true) }
        assert registros() == [globex: ['INSERT']]
    }
}
