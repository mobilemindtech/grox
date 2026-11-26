package org.grox.management

import groovy.json.JsonOutput
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Log de acesso no logger {@code grox.access} ({@code grox.accessLog.format: text | json}). */
class AccessLog {

    static final Logger LOG = LoggerFactory.getLogger('grox.access')

    final boolean json

    AccessLog(String format) {
        this.json = format == 'json'
    }

    void log(GroxRequest req, GroxResponse resp, Throwable error, long startNanos, String route) {
        if (!LOG.infoEnabled) return
        long ms = (System.nanoTime() - startNanos).intdiv(1_000_000)
        int status = error ? 500 : (resp?.status ?: 500)
        String user = req.userPrincipal?.name ?: '-'
        String remote = req.clientAddr ?: '-'   // endereço real atrás de proxy confiável
        if (json) {
            LOG.info(JsonOutput.toJson([ts: System.currentTimeMillis(), remote: remote, user: user, method: req.method, path: req.path,
                                        status: status, ms: ms, route: route, bytes: resp?.size() ?: 0]))
        } else {
            LOG.info("${remote} ${user} \"${req.method} ${req.uri}\" ${status} ${ms}ms ${route}")
        }
    }
}
