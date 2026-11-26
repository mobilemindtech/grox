package org.grox.web

import groovy.util.logging.Slf4j
import org.grox.assets.AssetsModule
import org.grox.core.ArtefactType
import org.grox.core.BeansDsl
import org.grox.core.GroxModule
import org.grox.data.Persistence
import org.grox.http.HttpModule
import org.grox.routing.LinkGenerator
import org.grox.routing.UrlMappingsHolder
import org.grox.security.SecurityModule
import org.grox.view.GspModule
import org.grox.web.interceptor.InterceptorApi
import org.grox.web.interceptor.InterceptorRegistry

/**
 * Compatibilidade com o Grails: descritores {@code *GrailsPlugin}, {@code doWithSpring} e
 * {@code resources.groovy}, UrlMappings, contexto web, interceptors, controllers e BootStrap.
 */
@Slf4j
class GrailsModule extends GroxModule implements org.grox.view.GspContributor {

    /** Descritores {@code *GrailsPlugin} das bibliotecas. */
    List<grails.plugins.Plugin> plugins = []
    UrlMappingsHolder urlMappings
    WebContext webContext
    ControllerRouter router
    private final List<Object> bootStraps = []

    /** O bean {@code grailsApplication}. */
    GroxGrailsApplication grailsApplication

    int getOrder() { 500 }

    /** {@code x as JSON} nas GSPs. */
    List<String> getGspImports() { ['grails.converters.JSON'] }

    /** Tags {@code f:} (subconjunto do plugin fields), usadas pelas views do scaffold. */
    List<Class<?>> getTagLibClasses() { [org.grox.web.scaffolding.FieldsTagLib] }

    /** Descritores {@code *GrailsPlugin}, UrlMappings, BootStrap, controllers e interceptors. */
    @Override
    @groovy.transform.CompileStatic
    List<ArtefactType> getArtefactTypes() {
        [ArtefactType.assignableTo('Plugin', ArtefactType.MARKER + 20, grails.plugins.Plugin),
         ArtefactType.suffix('UrlMappings', true), ArtefactType.suffix('BootStrap', true),
         ArtefactType.suffix('Controller'), ArtefactType.suffix('Interceptor')]
    }

    /** Cedo, antes do GORM: domain classes e configurações podem ler {@code Holders.grailsApplication}. */
    @Override
    void prepare() {
        grailsApplication = new GroxGrailsApplication(runtime.application)
        grails.util.Holders.grailsApplication = grailsApplication
    }

    Map<String, ControllerArtefact> getControllers() {
        webContext.controllers
    }

    @Override
    void initialize() {
        // encodeAsJSON() com o JSON do Grails (entidades, erros de validação, datas)
        org.grox.view.CodecExtensions.json = { Object v -> org.grox.web.json.JsonSupport.toJson(v) } as java.util.function.Function<Object, String>
        // request.JSON como JSONObject do Grails (getString, optString, isNull...)
        org.grox.http.GroxRequest.jsonView = { Object parsed -> org.grails.web.json.JSONObject.wrap(parsed) } as java.util.function.UnaryOperator<Object>
    }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        beans.grailsApplication = grailsApplication
        beans.groovyPageRenderer = new grails.gsp.PageRenderer(runtime)
    }

    /** Beans no DSL do Spring: {@code doWithSpring()} dos plugins, depois o da Application e o {@code resources.groovy}. */
    @Override
    void contributeApplicationBeans(Map<String, Object> beans) {
        Map<String, Object> defined = [:]
        plugins = runtime.application.getArtefacts('Plugin').collect { Class<?> c ->
            def p = (grails.plugins.Plugin) c.getDeclaredConstructor().newInstance()
            p.grailsApplication = grailsApplication
            p
        }
        beans.pluginManager = new grails.plugins.GrailsPluginManager(plugins)
        plugins.each { p -> defined.putAll(evaluateBeans(p.doWithSpring(), beans + defined, p.getClass().simpleName)) }
        Class<?> appClass = runtime.application.applicationClass
        if (grails.boot.config.GrailsAutoConfiguration.isAssignableFrom(appClass)) {
            def appConfig = (grails.boot.config.GrailsAutoConfiguration) appClass.getDeclaredConstructor().newInstance()
            defined.putAll(evaluateBeans(appConfig.doWithSpring(), beans + defined, 'Application.doWithSpring'))
        }
        defined.putAll(BeansDsl.load(runtime.application.classLoader, beans + defined))
        defined.each { k, v -> if (!beans.containsKey(k)) beans[k] = v }
        runtime.managedBeans.addAll(defined.values())
    }

    private static Map<String, Object> evaluateBeans(Closure beans, Map<String, Object> existing, String source) {
        beans == null ? [:] : BeansDsl.evaluate(beans, existing, source)
    }

    @Override
    void beansReady(Map<String, Object> beans) {
        Object dateFormats = config.getProperty('grails.databinding.dateFormats', Object)
        org.grox.core.Conversions.dateFormats = dateFormats instanceof Collection ? ((Collection) dateFormats)*.toString() :
                (dateFormats ? dateFormats.toString().split(',')*.trim() : [])
        CustomEditors.registrars = beans.values().findAll { it instanceof org.springframework.beans.PropertyEditorRegistrar } as List
        org.grox.core.Conversions.editors = CustomEditors.LOOKUP   // também em params.int('x')/params.date('d')
        if (!beans.localeResolver) beans.localeResolver = new org.springframework.web.servlet.i18n.SessionLocaleResolver()
        // a mesma referência do mapa: beans acrescentados depois também aparecem
        grailsApplication.mainContext = new grails.core.ApplicationContext(beans)
    }

    @Override
    void contextReady() {
        plugins.each { p ->
            p.applicationContext = grailsApplication.mainContext
            p.doWithDynamicMethods()
            p.doWithApplicationContext()
        }
    }

    @Override
    void configureWeb() {
        runtime.phase('urlMappings') { urlMappings = UrlMappingsHolder.build(runtime.application.getArtefacts('UrlMappings')) }
        def views = runtime.module(GspModule).views
        // sessões, estáticos, management, endpoints e o filtro vêm do pipeline HTTP do core
        def http = runtime.module(HttpModule)
        def pipeline = http.pipeline
        webContext = new WebContext(
                application: grailsApplication,
                sessions: pipeline.sessions,
                urlMappings: urlMappings,
                viewRenderer: views,
                views: views,
                messages: runtime.messages,
                statics: http.statics,
                management: pipeline.management,
                endpoints: pipeline.endpoints,
                accessLog: pipeline.accessLog,
                securityHeaders: pipeline.securityHeaders)
        def securityModule = runtime.module(SecurityModule)
        def security = securityModule?.security
        webContext.assets = runtime.module(AssetsModule)?.server
        webContext.asyncTimeoutSeconds = config.getProperty('grox.async.timeout', Integer, 60)
        webContext.links = new LinkGenerator(urlMappings, webContext.controllers, config.getProperty('grails.serverURL', String))
        // token CSRF nos <g:form> (com a segurança desligada, nada)
        org.grox.view.taglib.FormTagLib.hiddenFields = security?.cfg?.csrfEnabled ? { org.grox.http.GroxRequest req ->
            [(org.grox.security.SecurityFilter.CSRF_PARAM): security.csrfToken(req)]
        } as java.util.function.Function<org.grox.http.GroxRequest, Map<String, String>> : null
        if (security) {
            webContext.security = security
            securityModule.requestFilter?.loginView = new GrailsLoginView(webContext)   // views/login/auth.gsp
        }
        webContext.interceptors = new InterceptorRegistry(runtime.application.getArtefacts('Interceptor').collect { Class<?> c ->
            Object i = runtime.autowirer.autowire(c.getDeclaredConstructor().newInstance())
            if (!(i instanceof InterceptorApi)) throw new IllegalStateException("${c.name} deve estar em grails-app/controllers para virar interceptor")
            (InterceptorApi) i
        })
        runtime.phase('controllers') { createControllers() }
        router = new ControllerRouter(webContext)
        pipeline.addRouter(router)
    }






    private void createControllers() {
        String defaultMode = config.getProperty('grox.controllers.concurrency', String, 'pool')
        int defaultPool = config.getProperty('grox.controllers.poolSize', Integer, Runtime.runtime.availableProcessors() * 2)
        def autowirer = runtime.autowirer
        for (Class<?> c : runtime.application.getArtefacts('Controller')) {
            def artefact = new ControllerArtefact(c)
            def executor = new ControllerExecutor(artefact, webContext, autowirer)
            Closure<Object> factory = { autowirer.autowire(c.getDeclaredConstructor().newInstance()) }
            String mode = artefact.concurrency ?: defaultMode
            artefact.dispatcher = switch (mode) {
                case 'request' -> new PerRequestControllerDispatcher(factory, executor)
                case 'pool' -> new PooledControllerDispatcher(artefact.poolSize ?: defaultPool, factory, executor)
                default -> throw new IllegalArgumentException("Concorrência inválida '${mode}' em ${c.name} (use 'pool' ou 'request')")
            }
            webContext.controllers[artefact.logicalName] = artefact
            log.debug("Controller ${artefact.logicalName} (${mode}): actions ${artefact.actions.keySet()}")
        }
    }

    /** Recarga leve: os dados continuam os mesmos, então o BootStrap não roda de novo. */
    @Override
    void bootStrap() {
        if (runtime.handoff?.keepData) return
        runtime.application.getArtefacts('BootStrap').each { Class<?> c ->
            Object b = runtime.autowirer.autowire(c.getDeclaredConstructor().newInstance())
            bootStraps << b
            Persistence.withSessionIfAvailable { callBootStrap(b, 'init') }
        }
    }

    private static void callBootStrap(Object b, String name) {
        if (!b.hasProperty(name)) return
        Object c = b[name]
        if (!(c instanceof Closure)) return
        Closure cl = (Closure) c
        cl.delegate = new BootStrapDelegate()
        cl.maximumNumberOfParameters == 0 ? cl.call() : cl.call((Object) null)
    }

    static class BootStrapDelegate {
        Object environments(Closure blocks) {
            grails.util.Environment.executeForCurrentEnvironment(blocks)
        }
    }

    @Override
    String summary() {
        "${controllers.size()} controllers"
    }

    @Override
    void stop(Shutdown shutdown) {
        if (!shutdown.keepData) bootStraps.each { callBootStrap(it, 'destroy') }
        webContext?.controllers?.values()?.each { it.dispatcher?.close() }
        router?.close()
    }

    @Override
    void release(Shutdown shutdown) {
        if (shutdown.reload) return
        if (shutdown.generation) plugins.each { p ->
            try { p.onShutdown([:]) } catch (Exception e) { log.warn("${p.getClass().simpleName}.onShutdown: ${e.message}") }
        }
    }
}
