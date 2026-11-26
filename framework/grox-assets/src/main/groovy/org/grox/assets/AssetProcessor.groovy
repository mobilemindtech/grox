package org.grox.assets

import java.nio.charset.StandardCharsets
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Processa assets com as convenções do asset-pipeline do Grails:
 * <ul>
 *   <li>raízes: cada subdiretório de {@code grails-app/assets} (javascripts, stylesheets, images, ...);
 *       o caminho lógico é relativo à raiz ({@code application.js}, {@code logo.png})</li>
 *   <li>JS: {@code //= require x}, {@code //= require_tree dir}, {@code //= require_self}</li>
 *   <li>CSS: {@code *= require x}, {@code *= require_tree dir}, {@code *= require_self} (em comentário)</li>
 *   <li>cada arquivo entra uma vez; {@code url(...)} relativos do CSS são reescritos para o asset</li>
 * </ul>
 */
class AssetProcessor {

    static final Pattern JS_DIRECTIVE = ~/^\s*\/\/=\s*(require_tree|require_self|require)\b\s*(\S*)\s*$/
    static final Pattern CSS_DIRECTIVE = ~/^\s*\*?=\s*(require_tree|require_self|require)\b\s*(\S*)\s*$/
    static final Pattern CSS_URL = ~/url\(\s*(['"]?)([^'")]+)\1\s*\)/
    static final Set<String> TEXT = ['js', 'css'] as Set

    /** Resultado do processamento: conteúdo e arquivos de que depende (para reload). */
    static class Result {
        byte[] content
        String contentType
        List<File> dependencies = []
        long lastModified
    }

    final List<File> roots

    AssetProcessor(File assetsDir) {
        this.roots = roots(assetsDir)
    }

    AssetProcessor(List<File> roots) {
        this.roots = roots
    }

    /** javascripts, stylesheets, images primeiro; depois as demais pastas em ordem alfabética. */
    static List<File> roots(File assetsDir) {
        if (!assetsDir?.directory) return []
        List<String> preferred = ['javascripts', 'stylesheets', 'images']
        List<File> dirs = assetsDir.listFiles().findAll { it.directory }.toList()
        dirs.sort { File a, File b ->
            int ia = preferred.indexOf(a.name), ib = preferred.indexOf(b.name)
            (ia < 0 ? 99 : ia) <=> (ib < 0 ? 99 : ib) ?: a.name <=> b.name
        }
    }

    /** Arquivo do caminho lógico (primeira raiz que o contém) ou null. */
    File find(String logical) {
        if (!logical || logical.contains('..') || logical.startsWith('/')) return null
        for (File root : roots) {
            File f = new File(root, logical)
            if (f.file) return f
        }
        null
    }

    String logicalPath(File f) {
        String abs = f.canonicalPath
        for (File root : roots) {
            String r = root.canonicalPath + File.separator
            if (abs.startsWith(r)) return abs.substring(r.length()).replace(File.separator, '/')
        }
        null
    }

    /** Todos os assets (caminhos lógicos), sem duplicar nomes entre raízes. */
    List<String> allLogicalPaths() {
        Set<String> seen = new LinkedHashSet<>()
        roots.each { File root ->
            root.eachFileRecurse(groovy.io.FileType.FILES) { File f ->
                if (!f.name.startsWith('.')) seen << root.toPath().relativize(f.toPath()).toString().replace(File.separator, '/')
            }
        }
        seen.toList().sort()
    }

    /**
     * @param urlFor caminho lógico → URL pública (usado na reescrita de url() do CSS)
     * @return null se o asset não existir
     */
    Result process(String logical, Closure<String> urlFor) {
        File file = find(logical)
        if (file == null) return null
        String ext = extension(logical)
        def result = new Result(contentType: StaticMime.of(ext))
        if (!(ext in TEXT)) {
            result.content = file.bytes
            result.dependencies << file
        } else {
            StringBuilder out = new StringBuilder()
            include(file, ext, new LinkedHashSet<File>(), out, result.dependencies, urlFor)
            result.content = out.toString().getBytes(StandardCharsets.UTF_8)
        }
        result.lastModified = result.dependencies*.lastModified().max() ?: 0L
        result
    }

    private void include(File file, String ext, Set<File> included, StringBuilder out, List<File> deps, Closure<String> urlFor) {
        File canonical = file.canonicalFile
        if (!included.add(canonical)) return
        deps << canonical
        List<String> lines = file.getText('UTF-8').readLines()
        Pattern directive = ext == 'js' ? JS_DIRECTIVE : CSS_DIRECTIVE
        boolean selfIncluded = false
        StringBuilder self = new StringBuilder()
        List<Object[]> plan = []
        for (String line : lines) {
            Matcher m = directive.matcher(line)
            if (m.matches() && (ext == 'js' || line.trim().startsWith('*') || line.trim().startsWith('='))) {
                plan << ([m.group(1), m.group(2)] as Object[])
            } else {
                self << line << '\n'
            }
        }
        for (Object[] d : plan) {
            String kind = (String) d[0]
            String arg = (String) d[1]
            if (kind == 'require_self') {
                appendSelf(out, file, self, ext, urlFor)
                selfIncluded = true
            } else if (kind == 'require') {
                File dep = resolve(file, arg, ext)
                // como o asset-pipeline: asset inexistente só gera aviso ("Unable to Locate Asset")
                if (dep == null) org.slf4j.LoggerFactory.getLogger(AssetProcessor).warn("${logicalPath(file)}: require '${arg}' não encontrado (ignorado)")
                else include(dep, ext, included, out, deps, urlFor)
            } else {
                File dir = resolveDir(file, arg)
                if (dir == null) { org.slf4j.LoggerFactory.getLogger(AssetProcessor).warn("${logicalPath(file)}: require_tree '${arg}' não encontrado (ignorado)"); continue }
                List<File> files = []
                dir.eachFileRecurse(groovy.io.FileType.FILES) { File f -> if (extension(f.name) == ext) files << f }
                files.sort { it.path }.each { include(it, ext, included, out, deps, urlFor) }
            }
        }
        if (!selfIncluded) appendSelf(out, file, self, ext, urlFor)
    }

    private void appendSelf(StringBuilder out, File file, StringBuilder self, String ext, Closure<String> urlFor) {
        String text = self.toString()
        if (ext == 'css') text = rewriteUrls(file, text, urlFor)
        out << text
        if (out.length() && out.charAt(out.length() - 1) != '\n' as char) out << '\n'
    }

    /** {@code url(logo.png)} / {@code url(../images/logo.png)} → URL do asset (com digest em produção). */
    String rewriteUrls(File cssFile, String css, Closure<String> urlFor) {
        String cssLogical = logicalPath(cssFile)
        String base = cssLogical?.contains('/') ? cssLogical.substring(0, cssLogical.lastIndexOf('/') + 1) : ''
        CSS_URL.matcher(css).replaceAll { java.util.regex.MatchResult mr ->
            String ref = mr.group(2).trim()
            if (ref ==~ /^([a-z]+:|\/|#|data:).*/) return Matcher.quoteReplacement(mr.group())
            String clean = ref.replaceAll(/[?#].*$/, '')
            String suffix = ref.substring(clean.length())
            String logical = [normalize(base + clean), normalize(clean.replaceAll(/^(\.\.\/)+[^\/]+\//, '')), clean.tokenize('/').last()]
                    .find { it && find(it) != null }
            logical ? Matcher.quoteReplacement("url('${urlFor.call(logical)}${suffix}')") : Matcher.quoteReplacement(mr.group())
        }
    }

    private File resolve(File from, String name, String ext) {
        String n = name.endsWith('.' + ext) ? name : name + '.' + ext
        File rel = new File(from.parentFile, n)
        if (rel.file) return rel
        find(normalize(n))
    }

    private File resolveDir(File from, String name) {
        String n = name == '.' ? '' : name
        File rel = new File(from.parentFile, n)
        if (rel.directory) return rel
        roots.collect { new File(it, n) }.find { it.directory }
    }

    static String normalize(String path) {
        Deque<String> parts = new ArrayDeque<>()
        path.tokenize('/').each { String p ->
            if (p == '..') {
                if (parts) parts.removeLast()
            } else if (p != '.') {
                parts.addLast(p)
            }
        }
        parts.join('/')
    }

    static String extension(String name) {
        int i = name.lastIndexOf('.')
        i < 0 ? '' : name.substring(i + 1).toLowerCase()
    }
}
