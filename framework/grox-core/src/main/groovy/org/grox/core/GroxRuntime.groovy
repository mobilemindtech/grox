package org.grox.core

import groovy.util.logging.Slf4j
import org.grox.core.reload.Handoff
import org.grox.http.NettyServer
import org.grox.http.RequestDispatcher
import org.grox.i18n.MessageSource
import org.grox.i18n.SpringMessageSource
import org.grox.service.ServiceRegistry

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sobe a aplicação: descobre artefatos e módulos ({@link GroxModule}), cria os services, chama os
 * módulos em cada fase (GORM, segurança, views, Grails, plugins), executa a injeção e inicia o Netty.
 * O que é de cada módulo fica no módulo: {@code runtime.module(GormModule).datastore}. Por
 * compatibilidade, as propriedades públicas dos módulos também são lidas direto no runtime
 * ({@code runtime.datastore}, {@code runtime.webContext}).
 */
@Slf4j
class GroxRuntime implements AutoCloseable {

    final GroxApplication application
    final String[] args
    ServiceRegistry services
    Autowirer autowirer
    MessageSource messages
    /** Módulos na ordem de subida. */
    List<GroxModule> modules = []
    /**
     * Beans que recebem injeção, {@code @PostConstruct} e registro de listeners, além dos services
     * (os do {@code resources.groovy} e do {@code doWithSpring}).
     */
    final List<Object> managedBeans = []
    /** Valores padrão que um módulo oferece a outro (ex.: o resolver de tenant da segurança). */
    final Map<String, Object> defaults = [:]
    RequestDispatcher dispatcher
    NettyServer server
    private final AtomicBoolean closed = new AtomicBoolean()
    /** Fechada para a próxima geração assumir ({@link #closeForReload}). */
    private final AtomicBoolean retired = new AtomicBoolean()

    /** Recarga em desenvolvimento: o que esta geração herda da anterior (null fora dela). */
    final Handoff handoff

    GroxRuntime(Class<?> applicationClass, String[] args) {
        this(applicationClass, args, null)
    }

    GroxRuntime(Class<?> applicationClass, String[] args, Handoff handoff) {
        this.application = new GroxApplication(applicationClass)
        this.args = args
        this.handoff = handoff
    }

    Config getConfig() {
        application.config
    }

    int getPort() {
        server.port
    }

    /** O módulo do tipo pedido, ou null se não estiver no classpath. */
    public <T extends GroxModule> T module(Class<T> type) {
        (T) modules.find { type.isInstance(it) }
    }

    /** Propriedades dos módulos lidas no runtime ({@code runtime.datastore}, {@code runtime.views}). */
    Object propertyMissing(String name) {
        for (GroxModule m : modules) {
            if (!(name in MODULE_BASE_PROPERTIES) && m.metaClass.hasProperty(m, name)) return m.getProperty(name)
        }
        throw new MissingPropertyException(name, GroxRuntime)
    }

    private static final Set<String> MODULE_BASE_PROPERTIES = ['runtime', 'order', 'name', 'config', 'artefactTypes', 'class', 'metaClass'] as Set

    /** Módulos do classpath ({@code META-INF/services/org.grox.core.GroxModule}), um por classe, por ordem. */
    static List<GroxModule> loadModules(ClassLoader loader) {
        Map<String, GroxModule> found = [:]
        for (GroxModule m : ServiceLoader.load(GroxModule, loader)) found.putIfAbsent(m.getClass().name, m)
        found.values().sort { GroxModule m -> m.order }
    }

    GroxRuntime start() {
        long t0 = System.nanoTime()
        modules = loadModules(application.classLoader)
        modules.each { it.runtime = this }
        phase('scan') { application.scan([GroxApplication.SERVICE] + modules.collectMany { it.artefactTypes }) }
        modules.each { it.prepare() }
        log.debug("Módulos: ${modules*.name.join(', ')}")
        modules.each { m -> phase("${m.name}.initialize") { m.initialize() } }

        long tBeans = System.nanoTime()
        services = new ServiceRegistry()
        application.getArtefacts('Service').each { services.register(it) }
        messages = new MessageSource(application.classLoader, 'messages',
                Locale.forLanguageTag(config.getProperty('grox.i18n.defaultLocale', String, Locale.default.toLanguageTag())))
        Map<String, Object> beans = [groxApplication: (Object) application, groxRuntime: this,
                                     messageSource: new SpringMessageSource(messages)]
        modules.each { it.contributeBeans(beans) }
        beans.putAll(services.beans)
        modules.each { it.contributeApplicationBeans(beans) }
        autowirer = new Autowirer(beans)
        services.targets.values().each { autowirer.autowire(it) }
        managedBeans.each { autowirer.autowire(it) }
        modules.each { it.beansReady(beans) }
        // @PostConstruct depois de todos os beans injetados, como no Spring
        (services.targets.values() + managedBeans).each { Autowirer.postConstruct(it) }
        modules.each { it.contextReady() }
        phase('services e beans', tBeans)

        modules.each { m -> phase("${m.name}.web") { m.configureWeb() } }
        modules.each { m -> phase("${m.name}.aguardando") { m.awaitReady() } }
        modules.each { m -> phase("${m.name}.bootStrap") { m.bootStrap() } }
        modules.each { m -> phase("${m.name}.started") { m.started() } }
        startupPhases.removeAll { k, v -> v == 0L }

        dispatcher = modules.findResult { it.createDispatcher() }
        if (dispatcher == null) throw new IllegalStateException('Nenhum módulo atende as requisições (createDispatcher)')
        if (handoff?.previous) {
            server = handoff.previous.server // o DevReloader troca a entrada depois
        } else {
            server = new NettyServer(
                    config.getProperty('server.address', String, '0.0.0.0'),
                    config.getProperty('server.port', Integer, 8080),
                    config.getProperty('grox.http.maxContentLength', Integer, 10 * 1024 * 1024),
                    config.getProperty('grox.http.workerThreads', Integer, 0),
                    handoff ? handoff.entry : dispatcher)
            server.requestTimeoutSeconds = config.getProperty('grox.http.requestTimeout', Integer, 30)
            server.idleTimeoutSeconds = config.getProperty('grox.http.idleTimeout', Integer, 60)
            server.maxConnections = config.getProperty('grox.http.maxConnections', Integer, 10_000)
            server.maxPipelined = config.getProperty('grox.http.maxPipelined', Integer, 16)
            server.start()
        }
        if (!handoff) Runtime.runtime.addShutdownHook(Thread.ofPlatform().unstarted { close() })

        long ms = (System.nanoTime() - t0).intdiv(1_000_000)
        if (Env.defaulted) {
            log.info("Ambiente ${application.environmentName} por padrão (defina GRAILS_ENV ou -Dgrails.env para outro)")
        }
        List<String> summary = modules.collect { it.summary() }.findAll()
        summary << "${services.beans.size()} services, ${services.actorCount} services-ator".toString()
        log.info("Grox iniciado em ${ms}ms: http://localhost:${port} (ambiente: ${application.environmentName}, ${summary.join(', ')})")
        log.info("Subida por fase (ms): ${startupPhases.collect { k, v -> "$k=$v" }.join(', ')}; " +
                "JVM até aqui: ${java.lang.management.ManagementFactory.runtimeMXBean.uptime}ms")
        this
    }

    /** Tempos de cada fase da subida (ms), no log e em {@code groxRuntime.startupPhases}; as de 0 ms saem. */
    final Map<String, Long> startupPhases = [:]

    void phase(String name, Closure body) {
        long t = System.nanoTime()
        body.call()
        phase(name, t)
    }

    void phase(String name, long since) {
        startupPhases[name] = (System.nanoTime() - since).intdiv(1_000_000) as Long
    }

    /** Classe presente no classpath da aplicação (plugin opcional). */
    boolean classPresent(String name) {
        try {
            Class.forName(name, false, application.classLoader)
            true
        } catch (ClassNotFoundException | LinkageError ignored) {
            false
        }
    }

    boolean isDevelopment() {
        application.development
    }

    /** Diretório de fontes usado com reload em desenvolvimento (null se não existir ou reload desligado). */
    File devDirectory(String key, String defaultPath) {
        if (!config.getProperty('grox.gsp.reload', Boolean, development)) return null
        File dir = new File(config.getProperty(key, String, defaultPath))
        dir.directory ? dir : null
    }

    /**
     * Fecha esta geração para a próxima assumir (recarga em desenvolvimento), sem tocar no servidor
     * nem nas sessões. Com {@code keepData}, também o Hibernate e a segurança continuam (são da próxima).
     */
    void closeForReload(boolean keepData) {
        if (closed.get() || !retired.compareAndSet(false, true)) return
        def shutdown = new GroxModule.Shutdown(true, keepData, true)
        modules.reverseEach { it.stop(shutdown) }
        services?.close()
        modules.reverseEach { it.release(shutdown) }
    }

    /**
     * Desligamento gracioso: health passa a OUT_OF_SERVICE, o servidor para de aceitar
     * conexões e espera as requisições em andamento (até {@code grox.shutdown.timeout} segundos).
     */
    @Override
    void close() {
        if (!closed.compareAndSet(false, true)) return
        modules.reverseEach { it.shuttingDown() }
        if (server != null) {
            server.stopAccepting()
            long deadline = System.currentTimeMillis() + config.getProperty('grox.shutdown.timeout', Integer, 30) * 1000L
            while (server.inFlight.get() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
            if (server.inFlight.get()) log.warn("Desligando com ${server.inFlight.get()} requisição(ões) ainda em andamento")
        }
        // geração já fechada por uma recarga (que falhou): sobram o servidor e as sessões
        boolean generation = retired.compareAndSet(false, true)
        def shutdown = new GroxModule.Shutdown(false, false, generation)
        if (generation) modules.reverseEach { it.stop(shutdown) }
        server?.close()
        if (generation) services?.close()
        modules.reverseEach { it.release(shutdown) }
        log.info('Grox finalizado')
    }
}
