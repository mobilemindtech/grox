package org.grox.core.reload

import groovy.util.logging.Slf4j
import org.grox.core.Config
import org.grox.core.Env
import org.grox.core.GroxRuntime

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.CRC32

/**
 * Recarga em desenvolvimento (a "camada 2" de {@code docs/guia.md}): a aplicação roda em classloaders
 * próprios e, quando as classes compiladas mudam, sobe uma nova geração sem reiniciar a JVM. O servidor
 * Netty e as sessões continuam os mesmos.
 * <ul>
 *   <li><b>leve</b>: só mudaram classes {@link Reloadable} (controllers, services, taglibs, init, jobs),
 *       {@code messages*.properties} ou {@code resources.groovy}. Troca só a camada recarregável e mantém
 *       Hibernate, segurança e dados; a geração nova sobe antes de a antiga sair.</li>
 *   <li><b>completa</b>: mudou domain, src/main/groovy ou a configuração ({@code application.yml}, {@code application.groovy}, {@code runtime.groovy} e os importados). Fecha tudo (menos servidor e
 *       sessões) e sobe de novo, como um restart; as requisições esperam a nova geração.</li>
 * </ul>
 * Quem compila é o Gradle contínuo iniciado aqui ({@code grox.reload.gradlew}, passado pelo plugin) ou a IDE.
 */
@Slf4j
class DevReloader implements AutoCloseable {

    final String applicationClassName
    final String[] args
    final List<Path> classDirs
    final List<Path> resourceDirs
    final long intervalMs
    final SwitchableDispatcher entry = new SwitchableDispatcher()

    private AppClasses classes
    private Map<String, Long> resources
    private AppClassLoader stableLoader
    private volatile GroxRuntime current
    /** Última geração que subiu (fornece servidor e sessões à próxima completa). */
    private GroxRuntime last
    private int generation
    private Process compiler
    private Thread watcher
    private volatile boolean closed

    DevReloader(String applicationClassName, String[] args, List<Path> classDirs, List<Path> resourceDirs, long intervalMs) {
        this.applicationClassName = applicationClassName
        this.args = args
        this.classDirs = classDirs
        this.resourceDirs = resourceDirs
        this.intervalMs = intervalMs
    }

    /**
     * Ativa com {@code -Dgrox.reload.classes=<dirs>} (o {@code run} do plugin passa), no ambiente
     * development, e se {@code grox.reload.enabled} não for false (yml ou -D).
     */
    static boolean requested(Class<?> applicationClass) {
        if (!System.getProperty('grox.reload.classes') || !Env.development) return false
        String flag = System.getProperty('grox.reload.enabled')
        if (flag != null) return flag.toBoolean()
        Config.load(applicationClass.classLoader, Env.currentName).getProperty('grox.reload.enabled', Boolean, true)
    }

    static DevReloader create(Class<?> applicationClass, String[] args) {
        new DevReloader(applicationClass.name, args, paths(System.getProperty('grox.reload.classes')),
                paths(System.getProperty('grox.reload.resources')), Long.getLong('grox.reload.interval', 500L))
    }

    private static List<Path> paths(String value) {
        (value ?: '').split(File.pathSeparator).findAll().collect { Paths.get(it).toAbsolutePath() }
    }

    GroxRuntime getCurrent() {
        current
    }

    GroxRuntime start() {
        classes = AppClasses.scan(classDirs)
        resources = resourceChecksums()
        logPinned()
        current = startGeneration(null, false)
        last = current
        entry.switchTo(current.dispatcher)
        Runtime.runtime.addShutdownHook(Thread.ofPlatform().unstarted { close() })
        startCompiler()
        watcher = Thread.ofPlatform().daemon().name('grox-reload').start { watch() }
        log.info("Recarga ativa: ${classes.reloadable.size()} classes recarregáveis, ${classes.stable.size()} estáveis" +
                (compiler ? '; compilando com o Gradle contínuo' : '; compile pela IDE ou com gradle -t classes'))
        current
    }

    /** Uma geração: classloaders novos (a estável só se {@code keepData} for false) e um GroxRuntime. */
    private GroxRuntime startGeneration(GroxRuntime previous, boolean keepData) {
        generation++
        if (!keepData) stableLoader = new AppClassLoader("grox-stable-${generation}", classDirs, classes, classes.stable, true, ClassLoader.systemClassLoader)
        def loader = new AppClassLoader("grox-app-${generation}", classDirs, classes, classes.reloadable, false, stableLoader)
        classes.bytecode.clear() // cada classloader ficou com a sua parte
        Thread thread = Thread.currentThread()
        ClassLoader saved = thread.contextClassLoader
        thread.contextClassLoader = loader
        try {
            Class<?> app = Class.forName(applicationClassName, true, loader)
            new GroxRuntime(app, args, new Handoff(entry, stableLoader, previous, keepData)).start()
        } finally {
            thread.contextClassLoader = saved
        }
    }

    /**
     * Verifica as mudanças e sobe a nova geração, se houver. Devolve false se as classes não puderam
     * ser lidas (compilação em andamento), para tentar de novo.
     */
    synchronized boolean reload() {
        if (closed) return true
        AppClasses now
        try {
            now = AppClasses.scan(classDirs)
        } catch (Exception e) {
            log.debug("Recarga: classes ainda sendo escritas (${e})")
            return false
        }
        Map<String, Long> res = resourceChecksums()
        Set<String> changed = now.changedSince(classes)
        List<String> changedRes = (res.keySet() + resources.keySet()).findAll { res[it] != resources[it] }.sort()
        if (!changed && !changedRes) return true
        // leve: nenhuma classe estável mudou, a divisão em camadas é a mesma e o yml não mudou
        boolean light = current != null && now.stable == classes.stable && !changed.any { now.stable.contains(it) } &&
                !changedRes.any { it ==~ CONFIG_FILES }
        String what = (changed.collect { it.substring(it.lastIndexOf('.') + 1) } + changedRes).take(8).join(', ') +
                (changed.size() + changedRes.size() > 8 ? '...' : '')
        classes = now
        resources = res
        logPinned()
        long t0 = System.nanoTime()
        try {
            if (light) {
                GroxRuntime previous = current
                GroxRuntime next = startGeneration(previous, true)
                current = next
                last = next
                retire(entry.switchTo(next.dispatcher), previous, true)
            } else {
                retire(entry.hold(), current, false)
                current = null
                // servidor e sessões vêm da última geração que subiu, mesmo que uma recarga depois dela tenha falhado
                current = startGeneration(last, false)
                last = current
                entry.switchTo(current.dispatcher)
            }
            log.info("Recarga ${light ? 'leve' : 'completa'} em ${(System.nanoTime() - t0).intdiv(1_000_000)}ms (geração ${generation}): ${what}")
        } catch (Throwable e) {
            log.error("Recarga ${light ? 'leve' : 'completa'} falhou (${what})${light ? '; continua a geração anterior' : ''}", e)
            if (!light) {
                entry.switchTo(new SwitchableDispatcher.Failure(e))
                classes = new AppClasses() // a próxima mudança precisa de outra recarga completa
            }
        }
        true
    }

    /** Espera as requisições da geração anterior e a fecha. */
    private static void retire(SwitchableDispatcher.Target target, GroxRuntime runtime, boolean keepData) {
        if (target != null && !target.awaitIdle(10_000)) log.warn("Recarga: fechando a geração anterior com ${target.inFlight.get()} requisição(ões) em andamento")
        runtime?.closeForReload(keepData)
    }

    private void logPinned() {
        classes.pinned.each { String artefact, String by ->
            log.info("Recarga: ${artefact} é referenciado por ${by} (camada estável) e só recarrega na recarga completa")
        }
    }

    /** Arquivos de recursos que importam (views e assets já têm reload próprio): caminho → CRC. */
    /**
     * Arquivos de configuração: mudar um deles pede a recarga completa. Qualquer {@code .yml} ou {@code .groovy}
     * na raiz dos recursos, para incluir os importados por {@code spring.config.import}.
     */
    static final java.util.regex.Pattern CONFIG_FILES = ~/[^\/]+\.(ya?ml|groovy)/

    private Map<String, Long> resourceChecksums() {
        Map<String, Long> result = [:]
        for (Path dir : resourceDirs) {
            if (!Files.isDirectory(dir)) continue
            Files.walk(dir).withCloseable { stream ->
                stream.filter { Path p -> Files.isRegularFile(p) }.forEach { Path p ->
                    String rel = dir.relativize(p).toString().replace(File.separator, '/')
                    if (rel.startsWith('views/') || rel.startsWith('assets/') || rel.startsWith('public/')) return
                    try {
                        def crc = new CRC32()
                        crc.update(Files.readAllBytes(p))
                        result[rel] = crc.value
                    } catch (IOException ignored) {
                        // removido no meio da cópia
                    }
                }
            }
        }
        result
    }

    /** Assinatura barata (nomes, tamanhos e datas) para detectar escrita; o CRC só é lido depois. */
    private long signature() {
        long h = 17
        for (Path dir : classDirs + resourceDirs) {
            if (!Files.isDirectory(dir)) continue
            try {
                Files.walk(dir).withCloseable { stream ->
                    stream.forEach { Path p ->
                        try {
                            h = h * 31 + p.hashCode() + Files.getLastModifiedTime(p).toMillis() * 7 + Files.size(p)
                        } catch (IOException ignored) {
                            h = h * 31 + 1
                        }
                    }
                }
            } catch (IOException | UncheckedIOException ignored) {
                h = h * 31 + 2 // diretório sendo recriado pelo compilador
            }
        }
        h
    }

    /**
     * Polling. Com o Gradle contínuo, olha só o carimbo que ele grava ao fim de cada compilação bem-sucedida
     * ({@code grox.reload.stamp}): o compilador do Groovy limpa o diretório e passa segundos sem escrever,
     * e uma compilação com erro não deve derrubar a geração atual. Sem ele (IDE), recarrega quando a
     * assinatura dos diretórios muda e depois fica estável por um intervalo.
     */
    private void watch() {
        String stampPath = System.getProperty('grox.reload.stamp')
        if (compiler != null && stampPath) {
            watchStamp(Paths.get(stampPath))
            return
        }
        long last = signature()
        boolean dirty = false
        while (!closed) {
            try {
                Thread.sleep(intervalMs)
                long now = signature()
                if (now != last) {
                    last = now
                    dirty = true
                } else if (dirty) {
                    dirty = !reload()
                }
            } catch (InterruptedException ignored) {
                return
            } catch (Throwable e) {
                log.error('Recarga: erro no monitoramento das classes', e)
            }
        }
    }

    private void watchStamp(Path stamp) {
        long last = stampTime(stamp)
        boolean dirty = false
        while (!closed) {
            try {
                Thread.sleep(intervalMs)
                long now = stampTime(stamp)
                if (now != last) {
                    last = now
                    dirty = true
                }
                if (dirty) dirty = !reload()
            } catch (InterruptedException ignored) {
                return
            } catch (Throwable e) {
                log.error('Recarga: erro no monitoramento das classes', e)
            }
        }
    }

    private static long stampTime(Path stamp) {
        try {
            Files.getLastModifiedTime(stamp).toMillis()
        } catch (IOException ignored) {
            0L
        }
    }

    /** {@code grox.reload.gradlew} + {@code grox.reload.tasks}: build contínuo que atualiza as classes. */
    private void startCompiler() {
        String gradlew = System.getProperty('grox.reload.gradlew')
        String tasks = System.getProperty('grox.reload.tasks')
        if (!gradlew || !tasks || !Boolean.parseBoolean(System.getProperty('grox.reload.compile', 'true'))) return
        File wrapper = new File(gradlew)
        List<String> cmd = [wrapper.absolutePath, '-q', '--continuous'] + tasks.split(/\s+/).toList()
        try {
            compiler = new ProcessBuilder(cmd).directory(wrapper.parentFile).inheritIO().start()
        } catch (IOException e) {
            log.warn("Recarga: não foi possível iniciar o Gradle contínuo (${e.message}); compile pela IDE")
        }
    }

    @Override
    void close() {
        if (closed) return
        closed = true
        watcher?.interrupt()
        if (compiler != null) {
            compiler.descendants().forEach { it.destroy() }
            compiler.destroy()
        }
        synchronized (this) {
            (current ?: last)?.close()
        }
    }
}
