package sample

/** Interceptor global: mede o tempo de cada action e expõe no cabeçalho X-Tempo-Ms. */
class AuditoriaInterceptor {

    int order = HIGHEST_PRECEDENCE + 10

    /** Último afterView: roda também quando a action falha (é onde se limpa MDC, mede o tempo total...). */
    volatile String ultimoAfterView

    AuditoriaInterceptor() {
        matchAll().excludes(controller: 'erro')
    }

    boolean before() {
        request.setAttribute('auditoria.inicio', System.nanoTime())
        true
    }

    boolean after() {
        long inicio = request.getAttribute('auditoria.inicio') as long
        header 'X-Tempo-Ms', ((System.nanoTime() - inicio) / 1_000_000) as long
        log.debug("${request.method} ${controllerName}/${actionName}")
        true
    }

    void afterView() {
        ultimoAfterView = "${controllerName}/${actionName}:${throwable?.getClass()?.simpleName}"
    }
}
