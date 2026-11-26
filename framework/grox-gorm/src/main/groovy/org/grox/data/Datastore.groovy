package org.grox.data

import com.zaxxer.hikari.HikariDataSource
import groovy.util.logging.Slf4j
import org.grox.core.Config
import org.grox.data.tenant.TenantSupport
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder
import org.hibernate.boot.registry.StandardServiceRegistryBuilder

/**
 * Hibernate 7 + HikariCP configurados a partir de {@code dataSource} / {@code hibernate}
 * do {@code application.yml}, com as domain classes mapeadas via {@link OrmXml}.
 */
@Slf4j
class Datastore implements AutoCloseable, org.grails.orm.hibernate.HibernateDatastore {

    static final Map<String, String> DB_CREATE = ['create-drop': 'create-drop', create: 'create', update: 'update',
                                                  validate: 'validate', none: 'none']

    final Map<Class, DomainClass> domains
    final HikariDataSource dataSource
    /** Pronto quando o Hibernate termina de subir (em outra thread, se {@code async}). */
    private final java.util.concurrent.CompletableFuture<SessionFactory> ready
    final String ormXml

    /**
     * @param async se true, o {@code SessionFactory} (a parte cara: mapeamento, proxies, schema) é construído
     *              numa thread própria, e {@link #getSessionFactory()} espera por ele. A subida da aplicação
     *              continua em paralelo.
     */
    Datastore(Config config, Collection<Class<?>> domainClasses, ClassLoader loader, boolean async = false) {
        if (!System.getProperty('org.jboss.logging.provider')) System.setProperty('org.jboss.logging.provider', 'slf4j')
        this.domains = DomainClass.buildAll(domainClasses, async)
        String dbCreate = config.getProperty('dataSource.dbCreate', String, 'none')
        if (!DB_CREATE.containsKey(dbCreate)) throw new IllegalArgumentException("dataSource.dbCreate inválido: ${dbCreate}")
        resolveJoinTableClashes(dbCreate != 'none' && dbCreate != 'validate')
        this.ormXml = OrmXml.generate(domains.values())
        // diagnóstico: o mapeamento gerado para o Hibernate num arquivo (grox.data.ormXmlFile)
        String ormFile = config.getProperty('grox.data.ormXmlFile', String)
        if (ormFile) new File(ormFile).setText(ormXml, 'UTF-8')
        Map ds = (Map) (config.get('dataSource') ?: [:])
        this.dataSource = createDataSource(ds, config)
        Map<String, Object> settings = [
                'hibernate.connection.datasource'   : dataSource,
                'hibernate.hbm2ddl.auto'            : DB_CREATE[dbCreate],
                'hibernate.show_sql'                : config.getProperty('dataSource.logSql', Boolean, false),
                'hibernate.format_sql'              : config.getProperty('dataSource.formatSql', Boolean, false),
                'hibernate.physical_naming_strategy': GroxNamingStrategy.name,
                'hibernate.auto_quote_keyword'      : true,
                // listas com associações lazy: carrega até 16 pendentes numa consulta (IN), em vez de uma por linha (N+1).
                // A app sobrescreve com hibernate.default_batch_fetch_size no application.yml
                'hibernate.default_batch_fetch_size': 16,
        ] as Map<String, Object>
        // DDL que o 'update' aplicaria, só gravado em arquivo (nada é executado no banco)
        String script = config.getProperty('grox.schema.updateScript', String)
        if (script) {
            settings['jakarta.persistence.schema-generation.scripts.action'] = 'update'
            settings['jakarta.persistence.schema-generation.scripts.create-target'] = new File(script).absolutePath
            settings['hibernate.hbm2ddl.script.format'] = false
            new File(script).delete()
            log.info("Script de atualização do schema (não executado): ${new File(script).absolutePath}")
        }
        configureMultiTenancy(config, settings)
        flatten('hibernate', config.get('hibernate')).each { k, v -> settings[k] = v }
        // depois do yml: um hibernate.dialect explícito (o app C declara MySQLDialect) desligava o modo safe em silêncio,
        // e o update fazia alter table ... modify em toda coluna diferente do mapeamento
        if (config.getProperty('grox.schema.update', String, 'safe') == 'safe') {
            Object explicit = settings['hibernate.dialect']
            if (explicit == null) {
                settings['hibernate.dialect_resolvers'] = SafeUpdateDialects.name
            } else {
                String safe = SafeUpdateDialects.safeFor(explicit instanceof Class ? ((Class) explicit).name : explicit.toString())
                if (safe != null) settings['hibernate.dialect'] = safe
                else log.warn("hibernate.dialect ${explicit}: sem versão safe do update; com dbCreate update, colunas existentes podem ser alteradas")
            }
        }

        String proxies = config.getProperty('grox.data.proxies', String, 'auto')
        Closure<SessionFactory> build = {
            long t0 = System.nanoTime()
            def bootstrap = new BootstrapServiceRegistryBuilder().applyClassLoader(loader).build()
            def registry = new StandardServiceRegistryBuilder(bootstrap).applySettings(settings).build()
            try {
                def sources = new MetadataSources(registry)
                sources.addInputStream(new ByteArrayInputStream(ormXml.getBytes('UTF-8')))
                def metadata = sources.buildMetadata()
                int semProxy = skipUnneededProxies(metadata, proxies)
                SessionFactory sf = metadata.buildSessionFactory()
                HasOneCascade.register(sf, domains.values())
                DomainEventBridge.register(sf)
                log.info("Datastore: ${domains.size()} domain classes, ${dataSource.jdbcUrl} (dbCreate: ${dbCreate}), " +
                        "Hibernate em ${(System.nanoTime() - t0).intdiv(1_000_000)}ms" + (semProxy ? ", ${semProxy} sem proxy lazy" : ''))
                sf
            } catch (Throwable t) {
                StandardServiceRegistryBuilder.destroy(registry)
                dataSource.close()
                log.error("Falha ao iniciar o Hibernate. orm.xml gerado:\n${ormXml}")
                throw t
            }
        }
        if (async) {
            ready = new java.util.concurrent.CompletableFuture<>()
            Thread.ofPlatform().name('grox-hibernate').start {
                Thread.currentThread().contextClassLoader = loader
                try {
                    ready.complete(build.call())
                } catch (Throwable t) {
                    ready.completeExceptionally(t)
                }
            }
        } else {
            ready = java.util.concurrent.CompletableFuture.completedFuture(build.call())
        }
    }

    /**
     * {@code grox.data.proxies: auto} (padrão): só entidades referenciadas por many-to-one ganham proxy lazy.
     * O Hibernate gera uma classe de proxy (ByteBuddy) por entidade na subida, e com os métodos dos traits do
     * Grox esse era o maior custo. Para as demais, a única diferença é que {@code load(id)} carrega na hora.
     * {@code all} mantém o proxy em todas.
     */
    private int skipUnneededProxies(org.hibernate.boot.Metadata metadata, String mode) {
        if (mode == 'all') return 0
        Set<String> targets = domains.values().collectMany { DomainClass d ->
            d.persistentProperties.values().findAll { it.kind == DomainProperty.Kind.MANY_TO_ONE || (it.kind == DomainProperty.Kind.ONE_TO_ONE && !it.mappedBy) }.collect { it.referencedType.name }
        } as Set<String>
        int n = 0
        metadata.entityBindings.each { org.hibernate.mapping.PersistentClass pc ->
            if (pc.lazy && !(pc.className in targets)) {
                pc.lazy = false
                n++
            }
        }
        n
    }

    /** O {@code SessionFactory}; com {@code async}, espera o fim da inicialização do Hibernate. */
    /**
     * Tabela de junção (hasMany unidirecional ou many-to-many) com o nome da tabela de uma entidade
     * (ex.: {@code Grupo.clientes} → {@code grupo_cliente} e a domain {@code GrupoCliente}): o GORM gera o mesmo
     * nome e o conflito só aparece ao gerar o schema. Com {@code dbCreate} que gera o schema, a tabela de
     * junção ganha o nome da propriedade; contra um banco existente, mantém o nome do GORM.
     */
    private void resolveJoinTableClashes(boolean generatesSchema) {
        Set<String> tables = domains.values().collect { (it.table ?: DomainClass.snake(it.type.simpleName)).toLowerCase() } as Set
        domains.values().each { DomainClass d ->
            d.persistentProperties.values().findAll { it.joinTable && it.joinTable.toLowerCase() in tables }.each { DomainProperty p ->
                if (generatesSchema) {
                    String renamed = "${p.joinTable}_${DomainClass.snake(p.name)}".toString()
                    log.warn("${d.type.simpleName}.${p.name}: a tabela de junção ${p.joinTable} é a tabela de uma entidade; gerando o schema como ${renamed}")
                    p.joinTable = renamed
                } else {
                    log.warn("${d.type.simpleName}.${p.name}: a tabela de junção ${p.joinTable} é também a tabela de uma entidade (conflito também no GORM)")
                }
            }
        }
    }

    /** {@code hibernateDatastore.mappingContext} (modelo de mapeamento do GORM, somente leitura). */
    org.grails.datastore.mapping.model.MappingContext getMappingContext() {
        new org.grails.datastore.mapping.model.MappingContext(domains)
    }

    SessionFactory getSessionFactory() {
        try {
            ready.join()
        } catch (java.util.concurrent.CompletionException e) {
            throw e.cause
        }
    }

    /** Espera o Hibernate (propaga a falha de inicialização, se houver). */
    void awaitReady() {
        getSessionFactory()
    }

    /** Só o modo DISCRIMINATOR (coluna de tenant) é suportado. */
    private void configureMultiTenancy(Config config, Map<String, Object> settings) {
        List<DomainClass> tenantDomains = domains.values().findAll { it.multiTenant }
        String mode = config.getProperty('grails.gorm.multiTenancy.mode', String, tenantDomains ? 'DISCRIMINATOR' : 'NONE').toUpperCase()
        if (mode == 'NONE') return
        if (mode != 'DISCRIMINATOR') throw new UnsupportedOperationException("grails.gorm.multiTenancy.mode ${mode} não suportado (use DISCRIMINATOR)")
        Set<Class> types = tenantDomains.collect { it.tenantProperty.type } as Set
        if (types.size() > 1) throw new IllegalStateException("tipos de tenantId diferentes entre as domain classes: ${types*.simpleName}")
        tenantType = types ? types.first() : String
        settings['hibernate.tenant_identifier_resolver'] = new TenantSupport.HibernateResolver()
        multiTenancy = true
        log.info("Multi-tenancy DISCRIMINATOR: ${tenantDomains*.name}")
    }

    boolean multiTenancy
    Class<?> tenantType = String

    private static HikariDataSource createDataSource(Map ds, Config config) {
        def hikari = new HikariDataSource()
        hikari.jdbcUrl = config.getProperty('dataSource.url', String, 'jdbc:h2:mem:groxDb;DB_CLOSE_DELAY=-1')
        hikari.username = config.getProperty('dataSource.username', String, 'sa')
        hikari.password = config.getProperty('dataSource.password', String, '')
        String driver = config.getProperty('dataSource.driverClassName', String)
        if (driver) hikari.driverClassName = driver
        hikari.poolName = 'grox'
        (ds.get('properties') instanceof Map ? (Map) ds.get('properties') : [:]).each { k, v -> hikari[k.toString()] = v }
        hikari
    }

    static Map<String, Object> flatten(String prefix, Object value) {
        Map<String, Object> out = [:]
        if (value instanceof Map) {
            ((Map) value).each { k, v -> out.putAll(flatten("${prefix}.${k}", v)) }
        } else if (value != null) {
            out[prefix] = value
        }
        out
    }

    @Override
    void close() {
        if (ready.isDone() && !ready.isCompletedExceptionally()) ready.join().close()
        dataSource?.close()
    }
}
