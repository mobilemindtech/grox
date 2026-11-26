package org.grox.markdown

import org.grox.http.Html
import org.grox.view.ViewEngine
import org.grox.view.gsp.GspRenderContext
import org.grox.view.gsp.GspTemplateEngine
import org.grox.view.gsp.ViewLocator

import java.util.concurrent.ConcurrentHashMap

/**
 * Views {@code .md}: o arquivo é uma GSP ({@code ${}}, {@code <g:link>}, as TagLibs da aplicação) cuja saída é
 * Markdown, convertido em HTML pelo commonmark. O front matter dá o {@code title} e o {@code layout} da página
 * (as demais chaves viram {@code <meta>}, lidas no layout com {@code pageProperty(name: 'meta.chave')}); sem
 * {@code layout}, valem as regras de sempre (layout do controller, layout padrão). Templates ({@code _nome.md})
 * saem sem o documento em volta.
 */
class MarkdownViews implements ViewEngine {

    final ViewLocator locator
    final GspTemplateEngine gsp
    final boolean reload
    private final Map<String, GspTemplateEngine.CompiledGsp> cache = new ConcurrentHashMap<>()

    MarkdownViews(ViewLocator locator, GspTemplateEngine gsp, boolean reload) {
        this.locator = locator
        this.gsp = gsp
        this.reload = reload
    }

    String getExtension() { 'md' }

    boolean exists(String path) {
        locator.find(path, 'md') != null
    }

    void render(String path, Map model, GspRenderContext rc) {
        def compiled = compiled(path)
        String markdown = rc.capture { gsp.render(compiled, model, rc) }.toString()
        def result = Markdown.parse(markdown)
        boolean template = path.substring(path.lastIndexOf('/') + 1).startsWith('_')
        rc.out.write(template ? result.html : document(result))
    }

    private GspTemplateEngine.CompiledGsp compiled(String path) {
        def c = cache.get(path)
        if (c != null && !(reload && c.stale)) return c
        def src = locator.find(path, 'md')
        if (src == null) throw new FileNotFoundException("view Markdown não encontrada: ${path}.md")
        c = gsp.compile(path, src, path + '.md')
        cache.put(path, c)
        c
    }

    static String document(Markdown.Result result) {
        def sb = new StringBuilder('<!DOCTYPE html>\n<html>\n<head>\n')
        result.frontMatter.each { String k, Object v ->
            String value = v instanceof List ? ((List) v).join(', ') : v?.toString()
            if (k == 'title') sb << '<title>' << Html.escape(value) << '</title>\n'
            else sb << '<meta name="' << Html.escape(k) << '" content="' << Html.escape(value) << '"/>\n'
        }
        sb << '</head>\n<body>\n' << result.html << '</body>\n</html>\n'
        sb.toString()
    }
}
