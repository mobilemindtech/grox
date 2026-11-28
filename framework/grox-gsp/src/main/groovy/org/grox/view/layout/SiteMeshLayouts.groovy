package org.grox.view.layout

import org.grox.core.ClassUtils
import org.grox.view.GspViews
import org.grox.view.gsp.GspRenderContext
import org.grox.view.ViewRequest
import org.sitemesh.BaseSiteMeshContext
import org.sitemesh.content.Content
import org.sitemesh.content.ContentProcessor
import org.sitemesh.content.ContentProperty
import org.sitemesh.content.tagrules.TagBasedContentProcessor
import org.sitemesh.content.tagrules.decorate.DecoratorTagRuleBundle
import org.sitemesh.content.tagrules.html.CoreHtmlTagRuleBundle
import org.sitemesh.content.tagrules.html.Sm2TagRuleBundle

import java.nio.CharBuffer

/**
 * Layouts via SiteMesh 3: a página renderizada é processada (title, head, body,
 * meta.*, page.* de {@code <content tag>}) e o layout GSP escolhido a decora.
 * No layout valem {@code <g:layoutBody/>} etc. e também {@code <sitemesh:write property="body"/>}.
 *
 * Escolha do layout (como no Grails): {@code <meta name="layout">}, {@code static layout}
 * do controller, {@code layouts/<controller>/<action>}, {@code layouts/<controller>},
 * {@code grails.sitemesh.default.layout}.
 */
class SiteMeshLayouts {

    final GspViews views
    final String defaultLayout
    final ContentProcessor processor = new TagBasedContentProcessor(
            new CoreHtmlTagRuleBundle(), new DecoratorTagRuleBundle(), new Sm2TagRuleBundle())

    SiteMeshLayouts(GspViews views, String defaultLayout) {
        this.views = views
        this.defaultLayout = defaultLayout
    }

    String decorate(String html, Map model, GspRenderContext rc) {
        def ctx = new LayoutContext(this, rc, model)
        Content content = processor.build(CharBuffer.wrap(html), ctx)
        String layout = findLayout(content, rc.webRequest)
        layout ? apply(ctx, layout, content, html) : html
    }

    /** {@code <g:applyLayout name="x">}. */
    String applyLayout(String name, String html, Map model, GspRenderContext rc) {
        String layout = "/layouts/${name}"
        if (views.engine.find(layout) == null) return html
        def ctx = new LayoutContext(this, rc, model)
        apply(ctx, layout, processor.build(CharBuffer.wrap(html), ctx), html)
    }

    private static String apply(LayoutContext ctx, String layout, Content content, String original) {
        Content decorated = ctx.decorate(layout, content)
        if (decorated == null) return original
        def sw = new org.apache.groovy.io.StringBuilderWriter()
        decorated.data.writeValueTo(sw)
        sw.toString()
    }

    private static final Object NO_LAYOUT = new Object()
    /** {@code static layout} de cada controller, lido por reflexão uma vez (antes, em toda resposta HTML). */
    private static final ClassValue<Object> STATIC_LAYOUT_VALUE = new ClassValue<Object>() {
        @Override
        protected Object computeValue(Class<?> type) {
            ClassUtils.staticProperty(type, 'layout') ?: NO_LAYOUT
        }
    }

    static Object staticLayout(Class<?> controller) {
        Object v = STATIC_LAYOUT_VALUE.get(controller)
        v.is(NO_LAYOUT) ? null : v
    }

    @groovy.transform.CompileStatic
    String findLayout(Content content, ViewRequest wr) {
        List<String> candidates = []
        String meta = property(content, 'meta.layout')
        if (meta) {
            candidates << meta
        } else {
            if (wr?.controllerClass != null) {
                Object st = staticLayout(wr.controllerClass)
                if (st) candidates << st.toString()
                candidates << "${wr.controllerName}/${wr.actionName}".toString()
                candidates << wr.controllerName
            }
            if (defaultLayout) candidates << defaultLayout
        }
        for (String c : candidates) {
            String path = '/layouts/' + c
            if (views.engine.find(path) != null) return path
        }
        null
    }

    static ContentProperty node(Content content, String path) {
        if (content == null) return null
        ContentProperty p = content.extractedProperties
        List<String> parts = path.split(/\./).toList()
        for (int i = 0; i < parts.size(); i++) {
            // <content tag="include.bottom"> vira o filho "include.bottom" de page: tenta o resto do nome inteiro primeiro
            String rest = parts.subList(i, parts.size()).join('.')
            if (i > 0 && p.hasChild(rest)) return p.getChild(rest)
            if (!p.hasChild(parts[i])) return null
            p = p.getChild(parts[i])
        }
        p
    }

    static String property(Content content, String path) {
        ContentProperty p = node(content, path)
        p?.hasValue() ? p.value : null
    }

    static class LayoutContext extends BaseSiteMeshContext {
        final SiteMeshLayouts layouts
        final GspRenderContext rc
        final Map model

        LayoutContext(SiteMeshLayouts layouts, GspRenderContext rc, Map model) {
            super(layouts.processor)
            this.layouts = layouts
            this.rc = rc
            this.model = model
        }

        @Override
        String getPath() {
            rc.webRequest?.request?.path ?: '/'
        }

        @Override
        protected void decorate(String decoratorPath, Content content, Writer out) {
            renderLayout(layouts.views.engine, decoratorPath, content, out)
        }

        @groovy.transform.CompileStatic
        private void renderLayout(org.grox.view.gsp.GspTemplateEngine engine, String decoratorPath, Content content, Writer out) {
            org.grox.view.gsp.GspTemplateEngine.CompiledGsp layout = engine.find(decoratorPath)
            if (layout == null) return
            rc.withLayoutContent(content) { ->
                out.write(rc.capture { -> engine.render(layout, model, rc) }.toString())
            }
        }
    }
}
