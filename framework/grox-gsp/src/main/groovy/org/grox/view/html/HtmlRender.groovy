package org.grox.view.html

import org.apache.groovy.io.StringBuilderWriter
import org.grox.view.GspViews
import org.grox.view.ViewRequest
import org.grox.view.gsp.GspRenderContext

/**
 * DSL de HTML: o closure roda com um {@link HtmlBuilder} como delegate, e o resultado é decorado pelo layout
 * SiteMesh como uma view GSP ({@code meta(name: 'layout', content: 'main')} no {@code head}, o layout padrão ou
 * o informado). As tags {@code g:} ficam em {@code g.*} ({@code a(href: g.createLink(action: 'show'), 'Ver')},
 * {@code p g.message(code: 'x')}); um nome solto vira elemento, não chamada de método.
 * <pre>
 * render(layout: 'main') {
 *     h1 'Livros'
 *     ul { livros.each { l -> li { a(href: "/livros/${l.id}", l.titulo) } } }
 * }
 * </pre>
 * Começando por {@code html { }}, a saída ganha o {@code <!DOCTYPE html>}.
 */
class HtmlRender {

    /** @param views as views da aplicação (layouts e tags {@code g:}); null renderiza sem layout nem tags */
    static String render(Closure body, ViewRequest request, GspViews views, Map model = [:], String layout = null) {
        def sw = new StringBuilderWriter()
        def builder = new HtmlBuilder(sw)
        Closure c = (Closure) body.clone()
        c.delegate = builder
        c.resolveStrategy = Closure.DELEGATE_FIRST
        if (views == null) {
            invokeBody(c, builder)
            return doctype(sw.toString())
        }
        def rc = new GspRenderContext(request, views, new StringBuilderWriter())
        rc.bind {
            invokeBody(c, builder)
            String html = doctype(sw.toString())
            layout ? views.layouts.applyLayout(layout, html, model ?: [:], rc) : views.layouts.decorate(html, model ?: [:], rc)
        }
    }

    /** XML com o {@link groovy.xml.MarkupBuilder} puro ({@code render(contentType: 'text/xml') { ... }}). */
    static String xml(Closure body) {
        def sw = new StringBuilderWriter()
        def builder = new groovy.xml.MarkupBuilder(new IndentPrinter(sw, '', false))
        builder.doubleQuotes = true
        Closure c = (Closure) body.clone()
        c.delegate = builder
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.maximumNumberOfParameters == 0 ? c.call() : c.call(builder)
        sw.toString()
    }

    private static void invokeBody(Closure c, HtmlBuilder builder) {
        c.maximumNumberOfParameters == 0 ? c.call() : c.call(builder)
    }

    private static String doctype(String html) {
        html.startsWith('<html') ? '<!DOCTYPE html>' + html : html
    }
}
