package org.grox.view.gsp

import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.grox.view.taglib.TagLibRegistry

import java.util.concurrent.ConcurrentHashMap

/** Compila GSPs para classes (cache; recompila quando o arquivo muda, se reload). */
@Slf4j
class GspTemplateEngine {

    static class CompiledGsp {
        String path
        Class<? extends GspPage> type
        String[] texts
        String contentType
        ViewLocator.ViewSource source
        long lastModified
        String generatedSource
        /** Nome nos erros ({@code /livros/index.gsp}, {@code /paginas/ajuda.md}). */
        String view

        String getView() { view ?: path + '.gsp' }
        /** View {@code .md}: o {@code ${}} escapa também a pontuação do Markdown (links e imagens não são injetados). */
        boolean markdown

        private java.lang.reflect.Constructor<? extends GspPage> constructor

        /** Uma página nova (o construtor é buscado por reflexão uma vez só, não a cada view ou item de g:render). */
        GspPage newPage() {
            if (constructor == null) constructor = type.getDeclaredConstructor()
            constructor.newInstance()
        }

        boolean isStale() {
            source.file != null && source.lastModified != lastModified
        }
    }

    final ViewLocator locator
    final boolean reload
    final TagLibRegistry tags
    final ClassLoader parent
    private final Map<String, CompiledGsp> cache = new ConcurrentHashMap<>()
    private final Map<String, Boolean> missing = new ConcurrentHashMap<>()
    private final CompilerConfiguration config

    GspTemplateEngine(ViewLocator locator, boolean reload, TagLibRegistry tags, ClassLoader parent) {
        this.locator = locator
        this.reload = reload
        this.tags = tags
        this.parent = parent
        this.config = compilerConfiguration()
    }

    /** Imports padrão das GSPs pedidos pelos módulos ({@code GspContributor}), além do {@code SafeHtml}. */
    static volatile List<String> imports = []

    /** Mesma configuração no runtime e no pré-compilador. */
    static CompilerConfiguration compilerConfiguration() {
        def config = new CompilerConfiguration()
        config.scriptBaseClass = GspPage.name
        config.addCompilationCustomizers(new ImportCustomizer().addImports((['org.grox.http.SafeHtml'] + imports) as String[]))
        config.addCompilationCustomizers(new GspDirectCalls())
        config
    }

    static String className(String path) {
        'gsp' + path.replaceAll(/[^A-Za-z0-9]/, '_')
    }

    /** GSPs pré-compiladas no build (META-INF/grox/gsp-index.json), usadas quando não há reload. */
    private Map<String, Map> precompiled

    private synchronized Map<String, Map> precompiledIndex() {
        if (precompiled == null) {
            URL index = parent.getResource(GspPrecompiler.INDEX)
            precompiled = index ? (Map<String, Map>) new groovy.json.JsonSlurper().parse(index, 'UTF-8') : [:]
            if (precompiled) log.info("${precompiled.size()} GSPs pré-compiladas")
        }
        precompiled
    }

    private CompiledGsp loadPrecompiled(String path) {
        Map entry = precompiledIndex()[path]
        if (entry == null) return null
        Class type = Class.forName(entry.className.toString(), true, parent)
        new CompiledGsp(path: path, type: type, texts: entry.texts as String[], contentType: entry.contentType,
                source: new ViewLocator.ViewSource(url: parent.getResource(GspPrecompiler.INDEX)), lastModified: 0L,
                generatedSource: entry.source)
    }

    /** @return a GSP compilada ou null se não existir */
    CompiledGsp find(String path) {
        String p = normalize(path)
        CompiledGsp c = cache.get(p)
        if (c != null && !(reload && c.stale)) return c
        if (c == null && !reload && missing.containsKey(p)) return null
        synchronized (this) {
            c = cache.get(p)
            if (c != null && !(reload && c.stale)) return c
            if (!reload) {
                c = loadPrecompiled(p)
                if (c != null) {
                    cache.put(p, c)
                    return c
                }
            }
            ViewLocator.ViewSource src = locator.find(p)
            if (src == null) {
                cache.remove(p)
                missing.put(p, true)
                return null
            }
            c = compile(p, src)
            cache.put(p, c)
            return c
        }
    }

    /**
     * GSP gerada em tempo de execução (as views de scaffold), compilada na primeira vez e guardada pelo caminho.
     * Use um caminho que não exista entre as views, para uma view de verdade nunca ser encoberta.
     */
    CompiledGsp generated(String path, String sourceName, java.util.function.Supplier<String> text) {
        String p = normalize(path)
        CompiledGsp c = cache.get(p)
        if (c != null) return c
        synchronized (this) {
            c = cache.get(p)
            if (c == null) {
                c = compile(p, new ViewLocator.ViewSource(content: text.get(), name: sourceName))
                cache.put(p, c)
            }
            c
        }
    }

    static String normalize(String path) {
        String p = path.startsWith('/') ? path : '/' + path
        // '/dashboard/../project/_x' → '/project/_x' (nunca sobe acima da raiz das views)
        if (p.contains('/.')) {
            Deque<String> parts = new ArrayDeque<>()
            p.split('/').each { String s ->
                if (s == '..') { if (parts) parts.removeLast() }
                else if (s && s != '.') parts.addLast(s)
            }
            p = '/' + parts.join('/')
        }
        p.endsWith('.gsp') ? p.substring(0, p.length() - 4) : p
    }

    /** {@code view}: o nome nos erros (outros motores compilam como GSP arquivos de outra extensão, como o {@code .md}). */
    CompiledGsp compile(String path, ViewLocator.ViewSource src, String view = path + '.gsp') {
        long lastModified = src.lastModified
        def result = GspCompiler.compile(GspParser.parse(src.text, tags.namespaceNames, view), view)
        String className = className(path)
        def loader = new GroovyClassLoader(parent, config)
        Class type
        try {
            type = loader.parseClass(new GroovyCodeSource(result.source, className + '.groovy', '/groovy/gsp'), false)
        } catch (MultipleCompilationErrorsException e) {
            def m = e.message =~ /@ line (\d+)/
            int gspLine = m.find() ? gspLineOf(result.source, m.group(1) as int) : 0
            throw new GspException(view, gspLine, "erro de compilação: ${compileMessage(e)}", e)
        }
        log.debug("GSP compilada: ${view} (${src})")
        new CompiledGsp(path: path, type: type, texts: result.texts as String[], contentType: result.contentType,
                source: src, lastModified: lastModified, generatedSource: result.source, view: view)
    }

    static String compileMessage(MultipleCompilationErrorsException e) {
        def first = e.errorCollector.errors ? e.errorCollector.errors[0] : null
        if (first instanceof org.codehaus.groovy.control.messages.SyntaxErrorMessage) {
            // remove a referência ao código gerado ("@ line N, column M.")
            return first.cause.originalMessage ?: first.cause.message
        }
        e.message.readLines().drop(1).find { it.trim() } ?: e.message
    }

    static int gspLineOf(String generated, int line) {
        List<String> lines = generated.readLines()
        for (int i = Math.min(line, lines.size()) - 1; i >= 0; i--) {
            int idx = lines[i].lastIndexOf(GspCompiler.LINE_MARK)
            if (idx >= 0) return lines[i].substring(idx + GspCompiler.LINE_MARK.length()).trim() as int
        }
        0
    }

    /** Estático: numa classe dinâmica, cada {@code page.x = ...} passava pelo {@code setProperty} do {@code Script}. */
    @groovy.transform.CompileStatic
    void render(CompiledGsp c, Map model, GspRenderContext rc) {
        GspPage page = c.newPage()
        page.texts = c.texts
        page.viewPath = c.path
        page.markdown = c.markdown
        page.rc = rc
        GspBinding binding = new GspBinding(model, rc, rc.currentBinding)
        page.setBinding(binding)
        if (c.contentType && rc.webRequest != null) rc.webRequest.response.contentType = c.contentType
        rc.pushView(c.path)
        rc.bindings.push(binding)
        try {
            rc.bind { -> page.run() }
        } catch (GspException e) {
            throw e
        } catch (Throwable t) {
            StackTraceElement el = t.stackTrace.find { StackTraceElement e -> e.className.startsWith(c.type.name) }
            int line = el ? gspLineOf(c.generatedSource, el.lineNumber) : 0
            throw new GspException(c.view, line, t.toString(), t)
        } finally {
            rc.bindings.pop()
            rc.popView()
        }
    }
}
