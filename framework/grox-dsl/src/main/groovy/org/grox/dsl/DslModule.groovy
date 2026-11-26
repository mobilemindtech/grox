package org.grox.dsl

import groovy.util.logging.Slf4j
import org.grox.core.ArtefactType
import org.grox.core.BeansDsl
import org.grox.core.ClassUtils
import org.grox.core.Env
import org.grox.core.GroxModule
import org.grox.data.Persistence
import org.grox.data.json.GormJson
import org.grox.http.HttpModule
import org.grox.routing.LinkGenerator
import org.grox.routing.RouteMatch
import org.grox.security.AccessRuleSource
import org.grox.security.SecurityFilter
import org.grox.security.SecurityModule
import org.grox.http.GroxRequest
import org.grox.view.taglib.FormTagLib
import org.grox.view.CodecExtensions
import org.grox.view.GspModule

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Rotas sem o layout {@code grails-app/}: classes {@link Routes} (closures) e {@link Handler} (anotações)
 * do pacote da aplicação e as da DSL de projeto ({@link ProjectDsl}: {@code static app = { }} ou um script
 * {@link GroxScript}) viram um {@link DslRouter} no pipeline HTTP do core. Padrões de execução:
 * {@code grox.routes.execution} ({@code virtual}, o padrão, ou {@code actor}) e {@code grox.routes.poolSize}.
 */
@Slf4j
class DslModule extends GroxModule {

    DslRouter router
    /** A declaração da DSL de projeto, ou null. */
    ProjectSpec project
    /** Classes BootStrap já iniciadas (para o {@code destroy}). */
    private final List<Object> bootStraps = []

    /** Depois do módulo Grails: numa app com os dois, os controllers são consultados primeiro. */
    int getOrder() { 550 }

    @Override
    @groovy.transform.CompileStatic
    List<ArtefactType> getArtefactTypes() {
        [ArtefactType.assignableTo('Routes', ArtefactType.MARKER + 30, Routes),
         new ArtefactType('Handler', ArtefactType.MARKER + 31, false, { Class<?> c -> c.isAnnotationPresent(Handler) } as java.util.function.Predicate<Class<?>>),
         ArtefactType.suffix('BootStrap', true)]
    }

    /** A DSL de projeto, antes de qualquer módulo ler a configuração: o {@code config { }} entra como valores padrão. */
    @Override
    void prepare() {
        project = ProjectDsl.of(runtime.application.applicationClass)
        if (project?.config) runtime.config.mergeDefaults(project.config)
    }

    /** {@code beans { }} da DSL de projeto, no formato do {@code resources.groovy}. */
    @Override
    void contributeApplicationBeans(Map<String, Object> beans) {
        project?.beans?.each { Closure c ->
            Map<String, Object> defined = BeansDsl.evaluate(c, beans, "${runtime.application.applicationClass.simpleName}.app.beans")
            defined.each { k, v -> if (!beans.containsKey(k)) beans[k] = v }
            runtime.managedBeans.addAll(defined.values())
        }
    }

    @Override
    void configureWeb() {
        def http = runtime.module(HttpModule)
        List<Class<?>> routeClasses = runtime.application.getArtefacts('Routes')
        List<Class<?>> handlerClasses = runtime.application.getArtefacts('Handler')
        boolean projectRoutes = project?.routes
        if (http == null || (!routeClasses && !handlerClasses && !projectRoutes)) return
        String execution = config.getProperty('grox.routes.execution', String, 'virtual')
        int poolSize = config.getProperty('grox.routes.poolSize', Integer, Runtime.runtime.availableProcessors() * 2)
        if (!(execution in ['virtual', 'actor'])) throw new IllegalArgumentException("grox.routes.execution inválido: ${execution} (use virtual ou actor)")

        Map<String, RouteGroup> groups = new LinkedHashMap<>()
        // interceptores da raiz do routes { } da DSL de projeto: valem para todas as rotas da aplicação
        List<Interceptor> globals = []
        RouteGroup projectGroup = null
        routeClasses.each { Class<?> c ->
            Routes instance = (Routes) newInstance(c)
            def group = addGroup(groups, c)
            def builder = new RouteBuilder(group)
            Closure body = (Closure) instance.routes.clone()
            body.delegate = builder
            body.resolveStrategy = Closure.DELEGATE_FIRST
            body.call()
            builder.routes.each { Route r -> configure(group, r, instance, execution, poolSize, null) }
        }
        if (projectRoutes) {
            // rotas da DSL de projeto: o grupo tem o nome da classe da aplicação
            Class<?> app = runtime.application.applicationClass
            def group = addGroup(groups, app)
            def builder = new RouteBuilder(group)
            project.routes.each { Closure r ->
                Closure body = (Closure) r.clone()
                body.delegate = builder
                body.resolveStrategy = Closure.DELEGATE_FIRST
                body.call()
            }
            builder.routes.each { Route r -> configure(group, r, null, execution, poolSize, null) }
            handlerClasses = (handlerClasses + builder.handlers).unique()
            globals = builder.root.interceptors
            projectGroup = group
        }
        handlerClasses.each { Class<?> c ->
            Handler h = c.getAnnotation(Handler)
            if (h == null) throw new IllegalStateException("${c.name} em handlers sem @Handler")
            def group = addGroup(groups, c)
            Object shared = newInstance(c)
            List<Interceptor> own = Interceptor.of(c)
            c.methods.findAll { !Modifier.isStatic(it.modifiers) }.sort { it.name }.each { Method m ->
                String[] verb = verbOf(m)
                if (verb == null) return
                def r = new Route(group: group, action: m.name, httpMethod: verb[0], path: RouteBuilder.join(h.value(), verb[1]), method: m,
                        execution: h.execution() ?: null, poolSize: h.poolSize(),
                        access: RouteBuilder.accessOf(accessOf(m) ?: h.access()))
                r.interceptors = globals + own
                configure(group, r, shared, execution, poolSize, { -> newInstance(c) })
            }
        }
        groups.values().each { RouteGroup g ->
            if (g.type.isAnnotationPresent(Handler)) return
            g.routes.values().each { Route r -> r.interceptors = (g.is(projectGroup) ? [] : globals) + (r.scope?.chain() ?: []) }
        }

        def env = new DslEnvironment(sessions: http.pipeline.sessions, messages: runtime.messages, statics: http.statics,
                views: runtime.module(GspModule)?.views, application: runtime.application, beans: runtime.autowirer.beans, config: runtime.config)
        router = new DslRouter(groups, env)
        env.links = new LinkGenerator(router.holder, groups, config.getProperty('grox.serverURL', String))
        http.pipeline.addRouter(router)
        CodecExtensions.json = { Object v -> GormJson.toJson(v) } as java.util.function.Function<Object, String>
        def securityModule = runtime.module(SecurityModule)
        // access: das rotas (e dos @Handler), como um @Secured: antes das regras de URL
        securityModule?.security?.ruleSources?.put('grox-dsl', { GroxRequest req, RouteMatch m ->
            m?.controller instanceof RouteGroup ? ((RouteGroup) m.controller).routes[m.actionName]?.access : null
        } as AccessRuleSource)
        def security = securityModule?.requestFilter
        // página de login da app (views/login/auth.gsp), se o módulo Grails não definiu a dele
        if (security != null && security.loginView == null && env.views?.exists('/login/auth')) {
            security.loginView = new DslLoginView(env)
        }
        // token CSRF nos <g:form> (a cada geração: numa recarga, o filtro de segurança é outro); com o módulo
        // Grails presente o valor é o mesmo que ele definiu
        def filter = securityModule?.security
        FormTagLib.hiddenFields = filter?.cfg?.csrfEnabled ?
                { GroxRequest req -> [(SecurityFilter.CSRF_PARAM): filter.csrfToken(req)] } as java.util.function.Function<GroxRequest, Map<String, String>> : null
    }

    private Object newInstance(Class<?> c) {
        runtime.autowirer.autowire(c.getDeclaredConstructor().newInstance())
    }

    private static RouteGroup addGroup(Map<String, RouteGroup> groups, Class<?> c) {
        String name = ClassUtils.propertyName(c).replaceAll(/(Routes|Handler)$/, '') ?: ClassUtils.propertyName(c)
        if (groups.containsKey(name)) throw new IllegalStateException("Duas classes de rotas com o nome ${name}: ${groups[name].type.name} e ${c.name}")
        def g = new RouteGroup(name, c)
        groups[name] = g
        g
    }

    /** Execução (padrão ou da rota), instância e, para atores, o pool da rota. */
    private static void configure(RouteGroup group, Route r, Object instance, String execution, int poolSize, Closure<Object> newInstance) {
        if (group.routes.containsKey(r.action)) throw new IllegalStateException("Rota repetida em ${group.type.name}: ${r.action}")
        r.execution = r.execution ?: execution
        if (!(r.execution in ['virtual', 'actor'])) throw new IllegalArgumentException("${group.type.simpleName}.${r.action}: execution inválido ${r.execution}")
        r.poolSize = r.poolSize ?: poolSize
        r.instance = instance
        if (r.actor) r.pool = DslRouter.pool(r, r.poolSize, newInstance ?: { -> instance })
        group.routes[r.action] = r
    }

    /** {@code access} da anotação de verbo do método. */
    private static String[] accessOf(Method m) {
        if (m.isAnnotationPresent(Get)) return m.getAnnotation(Get).access()
        if (m.isAnnotationPresent(Post)) return m.getAnnotation(Post).access()
        if (m.isAnnotationPresent(Put)) return m.getAnnotation(Put).access()
        if (m.isAnnotationPresent(Patch)) return m.getAnnotation(Patch).access()
        if (m.isAnnotationPresent(Delete)) return m.getAnnotation(Delete).access()
        new String[0]
    }

    /** [método HTTP, caminho] da anotação do método, ou null. */
    private static String[] verbOf(Method m) {
        if (m.isAnnotationPresent(Get)) return ['GET', m.getAnnotation(Get).value()] as String[]
        if (m.isAnnotationPresent(Post)) return ['POST', m.getAnnotation(Post).value()] as String[]
        if (m.isAnnotationPresent(Put)) return ['PUT', m.getAnnotation(Put).value()] as String[]
        if (m.isAnnotationPresent(Patch)) return ['PATCH', m.getAnnotation(Patch).value()] as String[]
        if (m.isAnnotationPresent(Delete)) return ['DELETE', m.getAnnotation(Delete).value()] as String[]
        null
    }

    /**
     * O {@code bootstrap { init { } }} da DSL de projeto e as classes {@code BootStrap} do pacote da aplicação
     * ({@code def init = { }}, como no Grails), nessa ordem. As classes ficam com o módulo Grails, se presente.
     * Na recarga leve os dados continuam, então nada roda de novo.
     */
    @Override
    void bootStrap() {
        if (runtime.handoff?.keepData) return
        project?.inits?.each { Closure c -> Persistence.withSessionIfAvailable { callBootStrap(c, null) } }
        if (bootStrapClassesElsewhere()) return
        runtime.application.getArtefacts('BootStrap').each { Class<?> c ->
            Object b = runtime.autowirer.autowire(c.getDeclaredConstructor().newInstance())
            bootStraps << b
            Persistence.withSessionIfAvailable { callBootStrap(b, 'init') }
        }
    }

    /** Outro módulo (o Grails) também registra o tipo BootStrap e roda as classes. */
    private boolean bootStrapClassesElsewhere() {
        runtime.modules.any { m -> !m.is(this) && m.artefactTypes.any { it.name == 'BootStrap' } }
    }

    /** O closure {@code name} da classe (ou o próprio closure, com {@code name} null), com {@link BootStrapDelegate}. */
    private void callBootStrap(Object target, String name) {
        Closure cl
        if (name == null) {
            cl = (Closure) ((Closure) target).clone()
            cl.resolveStrategy = Closure.DELEGATE_FIRST
        } else {
            if (!target.hasProperty(name) || !(target[name] instanceof Closure)) return
            cl = (Closure) target[name]
        }
        cl.delegate = new BootStrapDelegate(runtime.autowirer.beans, config, runtime.application.applicationClass)
        cl.maximumNumberOfParameters == 0 ? cl.call() : cl.call((Object) null)
    }

    /** Delegate dos closures de BootStrap: {@code environments { }}, {@code config}, {@code log} e os beans pelo nome. */
    static class BootStrapDelegate {
        private final Map<String, Object> beans
        final org.grox.core.Config config
        final org.slf4j.Logger log

        BootStrapDelegate(Map<String, Object> beans, org.grox.core.Config config, Class<?> app) {
            this.beans = beans
            this.config = config
            this.log = org.slf4j.LoggerFactory.getLogger(app)
        }

        /** {@code environments { development { ... } }}: o bloco do ambiente atual. */
        Object environments(Closure blocks) {
            Map<String, Closure> found = [:]
            def collector = new Object() {
                def methodMissing(String n, Object args) {
                    Object[] a = (Object[]) args
                    if (a.length == 1 && a[0] instanceof Closure) found[n] = (Closure) a[0]
                    null
                }
            }
            Closure c = (Closure) blocks.clone()
            c.delegate = collector
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
            String env = Env.currentName
            Closure chosen = found[env] ?: found[env == Env.DEVELOPMENT ? 'dev' : env == Env.PRODUCTION ? 'prod' : env]
            chosen?.call()
        }

        Object propertyMissing(String name) {
            if (beans.containsKey(name)) return beans[name]
            throw new MissingPropertyException(name, BootStrapDelegate)
        }
    }

    @Override
    String summary() {
        router ? "${router.routeCount} rotas" : null
    }

    @Override
    void stop(Shutdown shutdown) {
        if (!shutdown.keepData) {
            bootStraps.reverse().each { callBootStrap(it, 'destroy') }
            project?.destroys?.reverse()?.each { Closure c -> callBootStrap(c, null) }
        }
        router?.close()
    }
}
