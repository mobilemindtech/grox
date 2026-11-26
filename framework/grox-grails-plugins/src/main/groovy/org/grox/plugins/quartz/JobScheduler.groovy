package org.grox.plugins.quartz

import groovy.util.logging.Slf4j
import org.codehaus.groovy.runtime.InvokerHelper
import org.grox.core.ClassUtils
import org.grox.data.Persistence

import java.time.ZonedDateTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Substituto simples do plugin Quartz: jobs em {@code grails-app/jobs} ({@code *Job}) com
 * {@code static triggers = { cron name:, cronExpression: ...; simple name:, repeatInterval:,
 * startDelay:, repeatCount: }} e método {@code execute()}.
 *
 * Cada execução roda numa virtual thread com sessão Hibernate disponível (como o
 * persistence interceptor do Quartz). {@code static concurrent = false} impede uma nova
 * execução enquanto a anterior não terminou. Desative tudo com {@code grox.jobs.enabled: false}
 * (ou {@code quartz.autoStartup: false}).
 */
@Slf4j
class JobScheduler implements AutoCloseable {

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name('grox-jobs').factory())
    private final ExecutorService workers = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name('grox-job-', 0).factory())
    private final List<Trigger> triggers = []
    private volatile boolean closed

    static class Trigger {
        String name
        Object job
        CronExpression cron
        long repeatInterval
        long startDelay
        int repeatCount = -1
        int fired
        boolean concurrent = true
        AtomicBoolean running = new AtomicBoolean()

        String toString() { "$name (${cron ? "cron '${cron.expression}'" : "a cada ${repeatInterval}ms"})" }
    }

    /** Lê {@code static triggers} do job; devolve os triggers criados (vazio se não houver). */
    List<Trigger> register(Object job) {
        Class<?> type = ClassUtils.userClass(job.getClass())
        Object dsl = ClassUtils.staticProperty(type, 'triggers')
        Object concurrent = ClassUtils.staticProperty(type, 'concurrent')
        List<Trigger> created = []
        if (dsl instanceof Closure) {
            Closure c = (Closure) ((Closure) dsl).clone()
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.delegate = new TriggersDsl(job: job, type: type, created: created)
            c.call()
        }
        created.each { it.concurrent = concurrent != false }
        triggers.addAll(created)
        created
    }

    private static class TriggersDsl {
        Object job
        Class<?> type
        List<Trigger> created

        void cron(Map args) {
            String expr = args.cronExpression ?: '0 0 6 * * ?'
            created << new Trigger(name: args.name ?: type.simpleName, job: job, cron: new CronExpression(expr.toString()),
                    startDelay: (args.startDelay ?: 0) as long)
        }

        void simple(Map args) {
            created << new Trigger(name: args.name ?: type.simpleName, job: job,
                    repeatInterval: (args.repeatInterval ?: 60000) as long, startDelay: (args.startDelay ?: 0) as long,
                    repeatCount: args.repeatCount != null ? (args.repeatCount as int) : -1)
        }

        void custom(Map args) {
            throw new UnsupportedOperationException("${type.simpleName}: trigger 'custom' não suportado no Grox")
        }
    }

    void start() {
        triggers.each { Trigger t ->
            log.info("Job agendado: ${ClassUtils.userClass(t.job.getClass()).simpleName} - $t")
            if (t.cron) scheduleCron(t, t.startDelay)
            else timer.scheduleWithFixedDelay({ fire(t) } as Runnable, t.startDelay, t.repeatInterval, TimeUnit.MILLISECONDS)
        }
    }

    private void scheduleCron(Trigger t, long minDelay) {
        if (closed) return
        ZonedDateTime now = ZonedDateTime.now()
        ZonedDateTime next = t.cron.nextAfter(minDelay > 0 ? now.plusNanos(minDelay * 1_000_000) : now)
        if (next == null) return
        long delay = Math.max(0, next.toInstant().toEpochMilli() - System.currentTimeMillis())
        timer.schedule({
            fire(t)
            scheduleCron(t, 0)
        } as Runnable, delay, TimeUnit.MILLISECONDS)
    }

    private void fire(Trigger t) {
        if (closed) return
        if (t.repeatCount >= 0 && t.fired > t.repeatCount) return
        if (!t.concurrent && !t.running.compareAndSet(false, true)) {
            log.debug("Job ${t.name} ainda em execução; disparo ignorado")
            return
        }
        t.fired++
        workers.submit {
            long t0 = System.nanoTime()
            try {
                Persistence.withSessionIfAvailable { InvokerHelper.invokeMethod(t.job, 'execute', null) }
                log.debug("Job ${t.name} executado em ${(System.nanoTime() - t0).intdiv(1_000_000)}ms")
            } catch (Throwable e) {
                log.error("Falha no job ${t.name}: ${e.message}", e)
            } finally {
                t.running.set(false)
            }
        }
    }

    /** Executa o job agora (útil em testes e no console). */
    void triggerNow(String name) {
        Trigger t = triggers.find { it.name == name || ClassUtils.userClass(it.job.getClass()).simpleName == name }
        if (t == null) throw new IllegalArgumentException("Job não encontrado: $name")
        fire(t)
    }

    List<Trigger> getTriggers() { triggers.asImmutable() }

    @Override
    void close() {
        closed = true
        timer.shutdownNow()
        workers.shutdown()
        workers.awaitTermination(10, TimeUnit.SECONDS)
    }
}
