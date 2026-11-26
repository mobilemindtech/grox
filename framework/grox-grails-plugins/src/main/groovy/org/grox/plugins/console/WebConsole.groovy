package org.grox.plugins.console

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.runtime.InvokerHelper
import org.grox.core.GroxRuntime
import org.grox.data.Persistence
import org.grox.http.Endpoint
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.security.Authentication
import org.grox.security.SecurityContextHolder
import org.grox.security.SecurityFilter
import org.grox.http.Html
import org.grox.web.GrailsModule
import org.grox.web.RequestContext
import org.grox.web.WebRequest
import org.slf4j.LoggerFactory

/**
 * Console web (como o plugin console do Grails): executa scripts Groovy dentro da aplicação.
 *
 * <p>Bindings: {@code ctx} (beans por nome), {@code grailsApplication}, {@code config},
 * {@code runtime}, {@code request}, {@code session}, {@code log}, {@code out} e cada service pelo
 * nome. Os pacotes das domain classes e dos artefatos são importados. O script roda com a
 * sessão de dados e a autenticação de quem executa (o tenant é o do usuário).</p>
 *
 * <p>Padrão: ligado só em development; com segurança exige {@code grox.console.access}
 * (padrão ROLE_ADMIN) e CSRF; sem segurança aceita só localhost ({@code Host} e endereço de
 * origem, sem proxy). Em qualquer caso, o {@code /execute} exige {@code Content-Type:
 * application/json} e recusa requisições de outro site ({@code Origin}, {@code Sec-Fetch-Site}):
 * um formulário ou script de outra página não consegue executar código.</p>
 */
@Slf4j
class WebConsole implements Endpoint {

    static final int MAX_TEXT = 200_000

    final String path
    final List<String> access
    final boolean localOnly
    final GroxRuntime runtime

    WebConsole(GroxRuntime runtime) {
        this.runtime = runtime
        def config = runtime.config
        this.path = config.getProperty('grox.console.path', String, '/console')
        Object a = config.get('grox.console.access')
        this.access = a != null ? (a as List)*.toString() : ['ROLE_ADMIN']
        this.localOnly = config.getProperty('grox.console.localOnly', Boolean, true)
    }

    private org.grox.security.SecurityFilter getSecurity() {
        runtime.module(org.grox.security.SecurityModule)?.security
    }

    private Object getSpringSecurityService() {
        runtime.module(org.grox.plugins.springsecurity.SpringSecurityModule)?.springSecurityService
    }

    String getName() { 'console' }

    List<String> accessFor(String path) { access }

    boolean handles(String p) {
        p == path || p == path + '/execute'
    }

    /** Sem segurança ativa, só localhost (a menos de grox.console.localOnly: false). */
    boolean allowedWithoutSecurity(GroxRequest req) {
        !localOnly || isLoopback(req.remoteAddr)
    }

    /** Nome do {@code Host} (sem porta) é localhost: bloqueia DNS rebinding. */
    static boolean isLoopbackHost(String host) {
        if (!host) return false
        String h = host.startsWith('[') ? host.substring(0, host.indexOf(']') + 1) : host.replaceAll(/:\d+$/, '')
        h = h.toLowerCase()
        h in ['localhost', '[::1]'] || h ==~ /127(\.\d{1,3}){3}/
    }

    /**
     * Barreiras contra CSRF e DNS rebinding.
     * @return o motivo da recusa, ou null
     */
    String rejection(GroxRequest req, boolean withSecurity) {
        if (!withSecurity && localOnly) {
            if (req.getHeader('X-Forwarded-For') || req.getHeader('Forwarded')) return 'console sem segurança não aceita requisições via proxy'
            if (!isLoopbackHost(req.getHeader('Host'))) return 'console sem segurança só aceita Host localhost'
        }
        if (req.path == path) return null   // a página pode ser aberta por link; só o /execute executa código
        String contentType = req.getHeader('Content-Type')?.toLowerCase()
        if (!contentType?.startsWith('application/json')) return 'envie Content-Type: application/json'
        String site = req.getHeader('Sec-Fetch-Site')
        if (site != null && site != 'same-origin' && site != 'none') return 'requisição de outro site'
        String origin = req.getHeader('Origin')
        if (origin != null && !sameOrigin(origin, req.hostHeader)) return 'Origin diferente do Host'
        null
    }

    private static boolean sameOrigin(String origin, String host) {
        int i = origin.indexOf('://')
        host != null && i > 0 && origin.substring(i + 3).equalsIgnoreCase(host)
    }

    static boolean isLoopback(String remote) {
        String host = remote?.replaceAll(/^\//, '')?.replaceAll(/:\d+$/, '')
        host in ['127.0.0.1', '0:0:0:0:0:0:0:1', '::1', 'localhost'] || host?.startsWith('127.')
    }

    /** Roda com a requisição, a autenticação e a sessão de dados de quem executa. */
    @Override
    GroxResponse handle(GroxRequest req) {
        def wr = new WebRequest(req, runtime.module(GrailsModule).webContext, null, null, [:])
        def auth = (Authentication) req.getAttribute(SecurityContextHolder.REQUEST_ATTR)
        RequestContext.with(wr) {
            SecurityContextHolder.with(auth) { Persistence.withSessionIfAvailable { handle(req, wr) } }
        }
    }

    GroxResponse handle(GroxRequest req, WebRequest wr) {
        String rejected = rejection(req, security != null)
        if (rejected != null) {
            log.warn("Console: requisição recusada (${rejected}) de ${req.remoteAddr}")
            return json(403, [error: rejected])
        }
        if (req.path == path) {
            if (req.method != 'GET') return status(405)
            return page(req)
        }
        if (req.method != 'POST') return status(405)
        Map body
        try {
            body = (Map) req.JSON
        } catch (Exception ignored) {
            body = null
        }
        if (!(body?.code instanceof String)) return json(400, [error: 'envie {"code": "..."}'])
        json(200, execute((String) body.code, body.rollback == true, wr))
    }

    /** @return result, resultType, output, error, durationMs, rolledBack */
    Map execute(String code, boolean rollback, WebRequest wr) {
        def out = new StringWriter()
        def printer = new PrintWriter(out, true)
        long t0 = System.nanoTime()
        Map<String, Object> response = [:]
        try {
            Closure run = { -> shell(bindings(wr, printer)).evaluate(code, 'Console.groovy') }
            Object result
            if (rollback && Persistence.available) {
                result = Persistence.withTransaction { status ->
                    try {
                        run.call()
                    } finally {
                        status.setRollbackOnly()
                    }
                }
                response.rolledBack = true
            } else {
                result = run.call()
            }
            response.result = truncate(result instanceof CharSequence ? result.toString() : InvokerHelper.inspect(result))
            response.resultType = result?.getClass()?.name
        } catch (MultipleCompilationErrorsException e) {
            response.error = truncate(e.message.replace('Console.groovy: ', 'linha '))
        } catch (Throwable e) {
            response.error = truncate(stackTrace(e))
        }
        printer.flush()
        response.output = truncate(out.toString())
        response.durationMs = (System.nanoTime() - t0).intdiv(1_000_000)
        log.info("console executado por ${wr?.request?.getAttribute('grox.security.authentication')?.name ?: wr?.request?.remoteAddr ?: 'código da aplicação'} " +
                "(${response.durationMs}ms${response.error ? ', com erro' : ''})")
        response
    }

    private GroovyShell shell(Binding binding) {
        def imports = new ImportCustomizer()
        Set<String> packages = ['grails.gorm.multitenancy', 'grails.converters', 'org.grox.data.query'] as Set
        ['Domain', 'Service', 'Controller'].each { type ->
            runtime.application.getArtefacts(type).each { packages << it.packageName }
        }
        imports.addStarImports(packages as String[])
        def config = new CompilerConfiguration()
        config.addCompilationCustomizers(imports)
        new GroovyShell(runtime.application.classLoader, binding, config)
    }

    private Binding bindings(WebRequest wr, PrintWriter out) {
        Map<String, Object> beans = runtime.services.beans
        def grailsApplication = runtime.module(org.grox.web.GrailsModule)?.grailsApplication ?: runtime.application
        def binding = new Binding()
        beans.each { k, v -> binding.setVariable(k, v) }
        // ctx com todos os beans (redisService, resources.groovy, plugins), como o applicationContext do console do Grails
        Map<String, Object> all = runtime.autowirer?.beans ?: beans
        binding.setVariable('ctx', new BeanLookup(all + [grailsApplication: grailsApplication, springSecurityService: springSecurityService]))
        binding.setVariable('grailsApplication', grailsApplication)
        binding.setVariable('config', runtime.config)
        binding.setVariable('runtime', runtime)
        binding.setVariable('request', wr?.request)
        binding.setVariable('session', wr?.getSession(true))
        binding.setVariable('springSecurityService', springSecurityService)
        binding.setVariable('log', LoggerFactory.getLogger('grox.console'))
        binding.setVariable('out', out)
        binding
    }

    /** {@code ctx.usuarioService}, {@code ctx.getBean('usuarioService')}, {@code ctx.beanNames}. */
    static class BeanLookup {
        private final Map<String, Object> beans

        BeanLookup(Map<String, Object> beans) {
            this.beans = beans
        }

        Object getBean(String name) {
            if (!beans.containsKey(name)) throw new IllegalArgumentException("bean inexistente: ${name} (disponíveis: ${beans.keySet().sort()})")
            beans[name]
        }

        Set<String> getBeanNames() {
            new TreeSet<>(beans.keySet())
        }

        Object propertyMissing(String name) {
            getBean(name)
        }
    }

    private static String stackTrace(Throwable e) {
        def sw = new StringWriter()
        e.printStackTrace(new PrintWriter(sw))
        // o que interessa: a mensagem e as linhas do script e da aplicação
        List<String> lines = sw.toString().readLines()
        List<String> kept = [lines[0]] + lines.drop(1).findAll { it.contains('(Console.groovy:') || it.startsWith('Caused by') || !it.trim().startsWith('at ') }.take(30)
        kept.join('\n')
    }

    private static String truncate(String s) {
        s == null ? null : (s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + '\n… (truncado)' : s)
    }

    private GroxResponse page(GroxRequest req) {
        String csrf = security?.cfg?.csrfEnabled ? security.csrfToken(req) : ''
        String html = WebConsole.getResourceAsStream('/org/grox/plugins/console/console.html').getText('UTF-8')
                .replace('@@CSRF@@', Html.escape(csrf))
                .replace('@@HEADER@@', SecurityFilter.CSRF_HEADER)
                .replace('@@EXECUTE@@', Html.escape(path + '/execute'))
                .replace('@@APP@@', Html.escape(runtime.application.applicationClass.simpleName))
                .replace('@@BEANS@@', Html.escape(runtime.services.beans.keySet().sort().join(', ')))
                .replace('@@DATA@@', Persistence.available ? 'true' : 'false')
        def resp = new GroxResponse(contentType: 'text/html')
        resp.setHeader('Cache-Control', 'no-store')
        resp.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; frame-ancestors 'self'")
        resp.writer.write(html)
        resp.committed = true
        resp
    }

    private static GroxResponse json(int status, Object body) {
        def resp = new GroxResponse(status: status, contentType: 'application/json')
        resp.setHeader('Cache-Control', 'no-store')
        resp.writer.write(JsonOutput.toJson(body))
        resp.committed = true
        resp
    }

    private static GroxResponse status(int code) {
        def r = new GroxResponse()
        r.sendError(code)
        r
    }
}
