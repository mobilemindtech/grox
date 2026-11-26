package org.grox.management

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.grox.core.Config
import org.grox.core.GroxApplication
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.session.RedisSessionRepository
import org.grox.session.SessionRepository

/**
 * Endpoints de operação (estilo Spring Boot Actuator), em {@code grox.management.basePath}:
 * {@code /health} (UP/DOWN com banco e Redis; 503 se DOWN ou desligando), {@code /info},
 * {@code /metrics} (JSON) e {@code /prometheus}.
 */
@Slf4j
class Management implements org.grox.http.Endpoint {

    String getName() { 'management' }

    /** Sem segurança, o management só limita metrics/prometheus pela configuração. */
    boolean allowedWithoutSecurity(GroxRequest req) { true }


    final String basePath
    /** {@code grox.management.metricsAccess}, ou null (então depende de haver segurança). */
    private final List<String> configuredMetricsAccess
    /** Se a segurança está ativa (decidido depois da criação: o filtro entra no pipeline depois). */
    private final java.util.function.BooleanSupplier securityActive
    final Metrics metrics = new Metrics()
    final GroxApplication application
    SessionRepository sessions
    /** Verificações dos módulos ({@code GroxModule.healthChecks}), além da do Redis das sessões. */
    Map<String, java.util.concurrent.Callable<Boolean>> healthChecks = [:]
    volatile boolean shuttingDown

    Management(Config config, GroxApplication application, boolean securityActive) {
        this(config, application, fixed(securityActive))
    }

    private static java.util.function.BooleanSupplier fixed(boolean value) {
        { -> value } as java.util.function.BooleanSupplier
    }

    Management(Config config, GroxApplication application, java.util.function.BooleanSupplier securityActive) {
        this.securityActive = securityActive
        this.application = application
        this.basePath = config.getProperty('grox.management.basePath', String, '/actuator')
        Object access = config.get('grox.management.metricsAccess')
        this.configuredMetricsAccess = access != null ? (access as List)*.toString() : null
    }

    /** Regras de metrics e prometheus: as configuradas; senão ROLE_ADMIN com segurança e permitAll sem. */
    List<String> getMetricsAccess() {
        configuredMetricsAccess != null ? configuredMetricsAccess : (securityActive.getAsBoolean() ? ['ROLE_ADMIN'] : ['permitAll'])
    }

    @groovy.transform.CompileStatic
    boolean handles(String path) {
        path.startsWith(basePath) && (path.length() == basePath.length() || path.charAt(basePath.length()) == '/' as char)
    }

    /** Regras de acesso: health e info públicos; metrics e prometheus conforme {@code metricsAccess}. */
    List<String> accessFor(String path) {
        String p = path.substring(basePath.length())
        p in ['/health', '/info'] ? ['permitAll'] : metricsAccess
    }

    GroxResponse handle(GroxRequest req) {
        String p = req.path.substring(basePath.length())
        def resp = new GroxResponse()
        resp.setHeader('Cache-Control', 'no-store')
        switch (p) {
            case '/health' -> {
                Map h = health()
                resp.status = h.status == 'UP' ? 200 : 503
                json(resp, h)
            }
            case '/info' -> json(resp, info())
            case '/metrics' -> json(resp, metrics.snapshot())
            case '/prometheus' -> {
                resp.contentType = 'text/plain; version=0.0.4'
                resp.writer.write(metrics.prometheus())
            }
            default -> {
                resp.status = 404
                json(resp, [status: 404, endpoints: ['health', 'info', 'metrics', 'prometheus'].collect { basePath + '/' + it }])
            }
        }
        resp.committed = true
        resp
    }

    Map health() {
        Map<String, Object> checks = [:]
        healthChecks.each { String name, java.util.concurrent.Callable<Boolean> probe -> checks[name] = check { probe.call() } }
        if (sessions instanceof RedisSessionRepository) checks.redis = check { ((RedisSessionRepository) sessions).ping() }
        boolean up = !shuttingDown && checks.values().every { it.status == 'UP' }
        [status: shuttingDown ? 'OUT_OF_SERVICE' : (up ? 'UP' : 'DOWN'), checks: checks]
    }

    /** A mensagem de falha (host, URL JDBC) só vai na resposta em development; sempre vai para o log. */
    private Map check(Closure<Boolean> probe) {
        try {
            probe.call() ? [status: 'UP'] : [status: 'DOWN']
        } catch (Exception e) {
            log.warn("health: ${e}")
            application.development ? [status: 'DOWN', error: e.message] : [status: 'DOWN']
        }
    }

    Map info() {
        [app        : application.applicationClass.simpleName,
         environment: application.environmentName,
         groovy     : GroovySystem.version,
         java       : Runtime.version().toString(),
         // Date.format() está no módulo groovy-dateutil (não incluído)
         startedAt  : java.time.Instant.ofEpochMilli(metrics.startedAt).toString()]
    }

    private static void json(GroxResponse resp, Object body) {
        resp.contentType = 'application/json'
        resp.writer.write(JsonOutput.toJson(body))
    }
}
