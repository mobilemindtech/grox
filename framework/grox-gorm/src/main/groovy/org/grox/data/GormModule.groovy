package org.grox.data

import groovy.util.logging.Slf4j
import org.grox.core.ArtefactType
import org.grox.core.ClassUtils
import org.grox.core.GroxModule
import org.grox.data.tenant.TenantSupport
import org.grails.datastore.mapping.multitenancy.TenantResolver

/**
 * GORM sobre o Hibernate: datastore (sobe em paralelo), beans {@code dataSource}/{@code sessionFactory}/
 * {@code transactionManager}, Data Services, listeners {@code @Listener} e o resolver de tenant.
 */
@Slf4j
class GormModule extends GroxModule {

    /** Chave do resolver de tenant; outro módulo pode oferecer um padrão em {@code runtime.defaults}. */
    static final String TENANT_RESOLVER = 'grails.gorm.multiTenancy.tenantResolverClass'

    Datastore datastore

    int getOrder() { 100 }

    /** Data Services (interface com {@code @Service}) e domain classes (trait {@link GormEntity}, aplicado pela AST). */
    @Override
    @groovy.transform.CompileStatic
    List<ArtefactType> getArtefactTypes() {
        [new ArtefactType('DataService', ArtefactType.MARKER, true, { Class<?> c -> DataServices.isDataService(c) } as java.util.function.Predicate<Class<?>>),
         ArtefactType.assignableTo('Domain', ArtefactType.MARKER + 10, GormEntity)]
    }

    /** Hibernate só sobe se houver domain classes ou {@code dataSource} configurado. */
    @Override
    void initialize() {
        List<Class<?>> domains = runtime.application.getArtefacts('Domain')
        if (!domains && config.get('dataSource') == null) return
        if (runtime.handoff?.keepData) {
            datastore = previous(GormModule).datastore // domain classes na camada estável, que não mudou
            return
        }
        // o Hibernate sobe em paralelo com o resto (services, views, controllers); ver D33
        datastore = new Datastore(config, domains, runtime.handoff?.dataClassLoader ?: runtime.application.classLoader,
                config.getProperty('grox.startup.parallel', Boolean, true))
        Gorm.init(datastore)
    }

    /** Usuário sem tenant tentando acessar dados multi-tenant: 403. */
    @Override
    Map<Class<? extends Throwable>, Integer> exceptionStatuses() {
        [(org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException): 403]
    }

    /** Conexão do pool válida em até 2 s. */
    @Override
    Map<String, java.util.concurrent.Callable<Boolean>> healthChecks() {
        datastore ? [db: { datastore.dataSource.connection.withCloseable { it.isValid(2) } } as java.util.concurrent.Callable<Boolean>] : [:]
    }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        if (!datastore) return
        beans.dataSource = datastore.dataSource
        beans.hibernateDatastore = datastore   // ref('hibernateDatastore') no resources.groovy
        beans.sessionFactory = new SessionFactoryBean(datastore)
        beans.transactionManager = new GroxTransactionManager()   // PlatformTransactionManager do Spring
    }

    @Override
    void contributeApplicationBeans(Map<String, Object> beans) {
        runtime.application.getArtefacts('DataService').each { Class<?> c ->
            beans[ClassUtils.propertyName(c)] = DataServices.create(c, runtime.application.classLoader)
        }
    }

    @Override
    void contextReady() {
        org.grox.data.json.GormJson.messages = runtime.messages   // mensagens dos erros de validação no JSON
        // métodos @Listener (eventos GORM) em services e beans do resources.groovy
        if (runtime.handoff) GormEvents.reset() // os listeners da geração anterior saem
        int listeners = (runtime.services.targets.values() + runtime.managedBeans).sum(0) { GormEvents.register(it) } as int
        if (listeners) log.debug("${listeners} listener(s) de eventos GORM registrados")
        configureTenancy()
    }

    /** Resolver de tenant: {@code grails.gorm.multiTenancy.tenantResolverClass} (com segurança, o usuário logado). */
    private void configureTenancy() {
        if (!datastore?.multiTenancy) return
        String cls = config.getProperty(TENANT_RESOLVER, String, (String) runtime.defaults[TENANT_RESOLVER])
        if (!cls) throw new IllegalStateException("Multi-tenancy sem ${TENANT_RESOLVER} (e sem segurança ativa)")
        Class<?> type = Class.forName(cls, true, runtime.application.classLoader)
        // se o resolver também é um bean (resources.groovy), usa a mesma instância
        Object bean = runtime.autowirer.beans.values().find { type.isInstance(it) }
        TenantResolver resolver = (TenantResolver) (bean ?: runtime.autowirer.autowire(type.getDeclaredConstructor().newInstance()))
        TenantSupport.configure(resolver, datastore.tenantType)
        log.info("Tenant resolver: ${cls}")
    }

    @Override
    void awaitReady() {
        datastore?.awaitReady()
        if (config.getProperty('grox.schema.scriptOnly', Boolean, false)) {
            // só gerar o script do schema (grox.schema.updateScript): encerra antes do BootStrap, sem gravar dados
            log.info('grox.schema.scriptOnly: encerrando antes do BootStrap')
            runtime.close()
            System.exit(0)
        }
    }

    @Override
    String summary() {
        "${datastore?.domains?.size() ?: 0} domain classes"
    }

    @Override
    void release(Shutdown shutdown) {
        if (shutdown.reload) {
            if (shutdown.keepData) return
            if (datastore != null) {
                datastore.close()
                Gorm.reset()
            }
            TenantSupport.reset()
            return
        }
        if (datastore != null) Gorm.reset()
        GormEvents.reset()
        TenantSupport.reset()
    }
}
