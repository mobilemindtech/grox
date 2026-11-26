package org.grox.http

import groovy.util.logging.Slf4j
import org.grox.core.GroxModule
import org.grox.management.AccessLog
import org.grox.management.Management
import org.grox.session.MemorySessionRepository
import org.grox.session.RedisSessionRepository
import org.grox.session.RequestSession
import org.grox.session.SessionRepository

/**
 * O atendimento HTTP do core ({@link HttpPipeline}): sessões (memória ou Redis), estáticos, management,
 * access log, cabeçalhos de segurança e proxies confiáveis. Sobe antes dos outros módulos web, que
 * plugam no pipeline o filtro de segurança, os assets, os endpoints e o roteador.
 */
@Slf4j
class HttpModule extends GroxModule {

    HttpPipeline pipeline
    StaticResources statics

    int getOrder() { 50 }

    SessionRepository getSessions() { pipeline?.sessions }

    Management getManagement() { pipeline?.management }

    @Override
    void configureWeb() {
        pipeline = new HttpPipeline(development: runtime.development)
        pipeline.sessions = runtime.handoff?.previous ? reuseSessions() : createSessionRepository()
        statics = new StaticResources(
                config.getProperty('grox.static.prefix', String, '/static'),
                runtime.devDirectory('grox.static.dir', 'src/main/resources/public'),
                runtime.application.classLoader, config.getProperty('grox.gsp.reload', Boolean, runtime.development))
        pipeline.resources << statics
        if (config.getProperty('grox.management.enabled', Boolean, true)) {
            HttpPipeline p = pipeline
            pipeline.management = new Management(config, runtime.application, { -> p.filter != null } as java.util.function.BooleanSupplier)
            pipeline.management.sessions = pipeline.sessions
            pipeline.endpoints << pipeline.management
            runtime.modules.each { pipeline.management.healthChecks.putAll(it.healthChecks()) }
        }
        if (config.getProperty('grox.accessLog.enabled', Boolean, false)) {
            pipeline.accessLog = new AccessLog(config.getProperty('grox.accessLog.format', String, 'text'))
        }
        pipeline.securityHeaders = securityHeaders()
        runtime.modules.each { pipeline.exceptionStatuses.putAll(it.exceptionStatuses()) }
        RequestSession.cookieSecure = config.getProperty('grox.session.cookie.secure', String, 'auto')
        String proxies = config.getProperty('grox.http.trustedProxies', String)
        if (proxies != null) GroxRequest.trustedProxies = proxies ? java.util.regex.Pattern.compile(proxies) : null
    }

    @Override
    RequestDispatcher createDispatcher() {
        pipeline
    }

    static final Map<String, String> DEFAULT_SECURITY_HEADERS = [
            'X-Content-Type-Options': 'nosniff',
            'X-Frame-Options'       : 'SAMEORIGIN',
            'Referrer-Policy'       : 'strict-origin-when-cross-origin',
    ]

    /**
     * {@code grox.http.securityHeaders}: {@code false} desliga; um mapa acrescenta ou troca
     * cabeçalhos, e valor vazio remove um dos padrões.
     */
    private Map<String, String> securityHeaders() {
        Object c = config.get('grox.http.securityHeaders')
        if (c != null && c.toString() == 'false') return [:]
        Map<String, String> h = new LinkedHashMap<>(DEFAULT_SECURITY_HEADERS)
        if (c instanceof Map) ((Map) c).each { k, v -> v == null || v.toString().empty ? h.remove(k.toString()) : h.put(k.toString(), v.toString()) }
        h
    }

    /** Sessões da geração anterior: os usuários continuam logados após a recarga. */
    private SessionRepository reuseSessions() {
        SessionRepository sessions = previous(HttpModule).sessions
        if (sessions instanceof RedisSessionRepository) ((RedisSessionRepository) sessions).classLoader = runtime.application.classLoader
        sessions
    }

    /** {@code grox.session.store}: memory (padrão) ou redis. */
    private SessionRepository createSessionRepository() {
        String cookie = config.getProperty('grox.session.cookieName', String, 'GROXSESSIONID')
        int timeout = config.getProperty('grox.session.timeout', Integer, 1800)
        String store = config.getProperty('grox.session.store', String, 'memory')
        return switch (store) {
            case 'memory' -> new MemorySessionRepository(cookie, timeout,
                    config.getProperty('grox.session.maxActive', Integer, MemorySessionRepository.DEFAULT_MAX_ACTIVE))
            case 'redis' -> new RedisSessionRepository(
                    config.getProperty('grox.session.redis.url', String, 'redis://localhost:6379'),
                    config.getProperty('grox.session.redis.keyPrefix', String, 'grox:session:'),
                    cookie, timeout, runtime.application.classLoader, sessionPackages())
            default -> throw new IllegalArgumentException("grox.session.store inválido: ${store} (use memory ou redis)")
        }
    }

    /**
     * Pacotes da aplicação aceitos na desserialização da sessão: o da classe Application, o
     * {@code grails.codegen.defaultPackage} e {@code grox.session.serialization.allow}.
     */
    private List<String> sessionPackages() {
        List<String> p = []
        String appPackage = runtime.application.applicationClass.packageName
        if (appPackage) p << appPackage
        String codegen = config.getProperty('grails.codegen.defaultPackage', String)
        if (codegen) p << codegen
        Object extra = config.get('grox.session.serialization.allow')
        if (extra instanceof Collection) p.addAll(((Collection) extra)*.toString())
        else if (extra) p.addAll(extra.toString().split(',')*.trim())
        p.unique()
    }

    @Override
    void shuttingDown() {
        pipeline?.management?.shuttingDown = true
    }

    @Override
    void stop(Shutdown shutdown) {
        pipeline?.close()
    }

    @Override
    void release(Shutdown shutdown) {
        if (shutdown.reload) return
        pipeline?.sessions?.close()
    }
}
