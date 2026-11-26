package org.grox.view

import org.grox.http.SafeHtml
import org.grox.view.gsp.GspException
import org.grox.view.gsp.GspRenderContext
import org.grox.view.gsp.GspTemplateEngine
import org.grox.view.layout.SiteMeshLayouts
import org.grox.view.taglib.TagLibRegistry

/**
 * Renderização de views e templates GSP, decoradas por layouts SiteMesh. Views em outros formatos vêm dos
 * {@link ViewEngine} registrados pelos módulos (Pug, Markdown), usados quando não há a {@code .gsp}.
 */
@groovy.util.logging.Slf4j
class GspViews implements ViewRenderer {

    final GspTemplateEngine engine
    final TagLibRegistry tags
    final SiteMeshLayouts layouts
    /** Motores de outros formatos, na ordem de registro. */
    final List<ViewEngine> engines = new java.util.concurrent.CopyOnWriteArrayList<>()

    GspViews(GspTemplateEngine engine, TagLibRegistry tags, String defaultLayout) {
        this.engine = engine
        this.tags = tags
        this.layouts = new SiteMeshLayouts(this, defaultLayout)
    }

    void register(ViewEngine other) {
        engines << other
    }

    /** O motor de outro formato que tem a view, ou null. */
    ViewEngine engineFor(String path) {
        engines.find { it.exists(path) }
    }

    /** Há a view (GSP ou de outro motor) em {@code path}, sem extensão. */
    boolean exists(String path) {
        engine.find(path) != null || engineFor(path) != null
    }

    @Override
    void render(String viewPath, Map model, ViewRequest wr) {
        def page = engine.find(viewPath)
        ViewEngine other = page == null ? engineFor(viewPath) : null
        if (page == null && other == null) {
            // sem log, o 404 fica mudo quando a app trata o erro (ex.: respond sem view depois de um POST)
            log.warn("View não encontrada: ${viewPath}.gsp (${wr.request.method} ${wr.request.uri}, " +
                    "${wr.controllerName}/${wr.actionName}); respondendo 404")
            wr.response.sendError(404, "View não encontrada: ${viewPath}.gsp")
            return
        }
        def sw = new org.apache.groovy.io.StringBuilderWriter()
        def rc = new GspRenderContext(wr, this, sw)
        rc.bind {
            if (page != null) engine.render(page, model, rc)
            else other.render(viewPath, model, rc)
            if (wr.response.contentType == null) wr.response.contentType = 'text/html'
            String html = sw.toString()
            if (isHtml(wr.response.contentType)) html = layouts.decorate(html, model, rc)
            wr.response.writeText(html)
        }
    }

    @Override
    void renderTemplate(String templatePath, Map attrs, ViewRequest wr) {
        def rc = new GspRenderContext(wr, this, new org.apache.groovy.io.StringBuilderWriter())
        SafeHtml html = rc.bind { renderTemplate(templatePath, attrs, rc) }
        if (wr.response.contentType == null) wr.response.contentType = 'text/html'
        wr.response.writer.write(html.toString())
    }

    /** {@code template}, {@code model}, {@code bean}, {@code collection}, {@code var}. */
    SafeHtml renderTemplate(String templatePath, Map attrs, GspRenderContext rc) {
        def template = engine.find(templatePath)
        ViewEngine other = template == null ? engineFor(templatePath) : null
        if (template == null && other == null) throw new GspException(null, 0, "template não encontrado: ${templatePath}.gsp")
        Map model = (Map) (attrs.model ?: [:])
        String var = attrs.var?.toString() ?: 'it'
        Closure one = { Map m -> template != null ? engine.render(template, m, rc) : other.render(templatePath, m, rc) }
        rc.capture {
            if (attrs.containsKey('collection')) {
                for (Object item : (attrs.collection ?: [])) one(model + [(var): item])
            } else if (attrs.containsKey('bean')) {
                one(model + [(var): attrs.bean])
            } else {
                one(model)
            }
        }
    }

    /** {@code form} → {@code /<controller>/_form}; {@code /shared/menu} → {@code /shared/_menu}. */
    static String templatePath(String template, GspRenderContext rc) {
        int i = template.lastIndexOf('/')
        String t = i >= 0 ? template.substring(0, i + 1) + '_' + template.substring(i + 1) : '_' + template
        if (t.startsWith('/')) return t
        String controller = rc.webRequest?.controllerName
        controller ? "/${controller}/${t}" : rc.currentViewDir + t
    }

    static boolean isHtml(String contentType) {
        contentType?.startsWith('text/html') || contentType?.startsWith('application/xhtml')
    }
}
