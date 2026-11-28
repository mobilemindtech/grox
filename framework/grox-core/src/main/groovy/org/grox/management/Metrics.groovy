package org.grox.management

import org.grox.http.GroxRequest
import org.grox.http.GroxResponse

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/** Contadores de requisições (total, por status, por rota) e requisições em andamento. */
class Metrics {

    @groovy.transform.CompileStatic
    static class RouteStats {
        final LongAdder count = new LongAdder()
        final LongAdder errors = new LongAdder()
        final LongAdder totalNanos = new LongAdder()
        final AtomicLong maxNanos = new AtomicLong()

        void record(long nanos, boolean error) {
            count.increment()
            if (error) errors.increment()
            totalNanos.add(nanos)
            maxNanos.accumulateAndGet(nanos, (long a, long b) -> Math.max(a, b))
        }

        Map toMap() {
            long c = count.sum()
            [count: c, errors: errors.sum(), avgMs: c ? round(totalNanos.sum() / c / 1e6) : 0, maxMs: round(maxNanos.get() / 1e6)]
        }
    }

    final long startedAt = System.currentTimeMillis()
    final AtomicInteger active = new AtomicInteger()
    final LongAdder total = new LongAdder()
    final Map<String, LongAdder> byStatus = new ConcurrentHashMap<>()
    final Map<String, RouteStats> byRoute = new ConcurrentHashMap<>()
    private static final String[] STATUS_BUCKETS = ['0xx', '1xx', '2xx', '3xx', '4xx', '5xx', '6xx', '7xx', '8xx', '9xx'] as String[]

    long begin() {
        active.incrementAndGet()
        System.nanoTime()
    }

    /** Estático e sem closures: roda em toda requisição. */
    @groovy.transform.CompileStatic
    void end(GroxRequest req, GroxResponse resp, Throwable error, long start, String route) {
        active.decrementAndGet()
        long nanos = System.nanoTime() - start
        int status = error != null ? 500 : (resp?.status ?: 500)
        total.increment()
        String bucket = STATUS_BUCKETS[Math.min(Math.max(status.intdiv(100) as int, 0), 9)]
        LongAdder s = (LongAdder) byStatus.get(bucket)
        if (s == null) {
            byStatus.putIfAbsent(bucket, new LongAdder())
            s = (LongAdder) byStatus.get(bucket)
        }
        s.increment()
        RouteStats r = (RouteStats) byRoute.get(route)
        if (r == null) {
            byRoute.putIfAbsent(route, new RouteStats())
            r = (RouteStats) byRoute.get(route)
        }
        r.record(nanos, status >= 500)
    }

    Map snapshot() {
        Runtime rt = Runtime.runtime
        [uptimeSeconds: (System.currentTimeMillis() - startedAt).intdiv(1000),
         http         : [active: active.get(), total: total.sum(), status: byStatus.collectEntries { k, v -> [(k): v.sum()] }],
         routes       : byRoute.collectEntries { k, v -> [(k): v.toMap()] }.sort(),
         jvm          : [heapUsedMb    : (rt.totalMemory() - rt.freeMemory()).intdiv(1024 * 1024),
                         heapMaxMb     : rt.maxMemory().intdiv(1024 * 1024),
                         processors    : rt.availableProcessors(),
                         platformThreads: Thread.allStackTraces.size()]]
    }

    /** Formato de exposição do Prometheus. */
    String prometheus() {
        StringBuilder sb = new StringBuilder()
        sb << '# TYPE grox_http_requests_active gauge\n' << "grox_http_requests_active ${active.get()}\n"
        sb << '# TYPE grox_http_requests_total counter\n'
        byStatus.each { k, v -> sb << "grox_http_requests_total{status=\"${k}\"} ${v.sum()}\n" }
        sb << '# TYPE grox_route_requests_total counter\n'
        byRoute.each { k, v -> sb << "grox_route_requests_total{route=\"${k}\"} ${v.count.sum()}\n" }
        sb << '# TYPE grox_route_errors_total counter\n'
        byRoute.each { k, v -> sb << "grox_route_errors_total{route=\"${k}\"} ${v.errors.sum()}\n" }
        sb << '# TYPE grox_route_seconds_sum counter\n'
        byRoute.each { k, v -> sb << "grox_route_seconds_sum{route=\"${k}\"} ${v.totalNanos.sum() / 1e9}\n" }
        sb << '# TYPE grox_route_seconds_max gauge\n'
        byRoute.each { k, v -> sb << "grox_route_seconds_max{route=\"${k}\"} ${v.maxNanos.get() / 1e9}\n" }
        Runtime rt = Runtime.runtime
        sb << '# TYPE grox_jvm_heap_used_bytes gauge\n' << "grox_jvm_heap_used_bytes ${rt.totalMemory() - rt.freeMemory()}\n"
        sb.toString()
    }

    private static double round(double v) {
        Math.round(v * 100) / 100d
    }
}
