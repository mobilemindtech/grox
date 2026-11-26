package org.grox.view.gsp

import org.grox.view.GspViews
import org.grox.http.SafeHtml
import org.grox.view.taglib.TagLibRegistry
import org.grox.view.ViewRequest
import org.sitemesh.content.Content

/**
 * Estado de uma renderização (página + templates + layouts), vinculado via
 * {@link ScopedValue}: pilha de writers, pageScope, conteúdo do layout.
 */
@groovy.transform.CompileStatic
class GspRenderContext {

    static final ScopedValue<GspRenderContext> CURRENT = ScopedValue.newInstance()

    final ViewRequest webRequest
    final GspViews views
    final Binding pageScope = new Binding()
    /** Página sendo decorada (para g:layoutBody, g:pageProperty...). */
    Content layoutContent
    private final Deque<Writer> writers = new ArrayDeque<>()
    private final Deque<String> viewStack = new ArrayDeque<>()

    GspRenderContext(ViewRequest webRequest, GspViews views, Writer out) {
        this.webRequest = webRequest
        this.views = views
        writers.push(out)
    }

    static GspRenderContext current() {
        if (!CURRENT.bound) throw new IllegalStateException('Nenhuma renderização GSP ativa nesta thread')
        CURRENT.get()
    }

    static GspRenderContext currentOrNull() {
        CURRENT.bound ? CURRENT.get() : null
    }

    <T> T bind(Closure<T> body) {
        if (CURRENT.bound && CURRENT.get().is(this)) return body.call()
        org.grox.core.Scoped.where(CURRENT, this, body)
    }

    TagLibRegistry getTags() {
        views.tags
    }

    Writer getOut() {
        writers.peek()
    }

    SafeHtml capture(Closure body) {
        def sw = new org.apache.groovy.io.StringBuilderWriter()
        writers.push(sw)
        try {
            body.call()
        } finally {
            writers.pop()
        }
        new SafeHtml(sw.toString())
    }

    /** Bindings das páginas em renderização (o topo é a página atual). */
    final Deque<GspBinding> bindings = new ArrayDeque<>()

    GspBinding getCurrentBinding() {
        bindings.peek()
    }

    void pushView(String path) {
        viewStack.push(path)
    }

    void popView() {
        viewStack.pop()
    }

    /** Diretório da view atual, para templates relativos fora de controllers. */
    String getCurrentViewDir() {
        String v = viewStack.peek()
        v ? v.substring(0, v.lastIndexOf('/') + 1) : '/'
    }

    Object withLayoutContent(Content content, Closure body) {
        Content previous = layoutContent
        layoutContent = content
        try {
            body.call()
        } finally {
            layoutContent = previous
        }
    }
}
