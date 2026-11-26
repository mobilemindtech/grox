package grails.gsp

import org.grox.core.GroxRuntime
import org.grox.view.GspViews
import org.grox.view.gsp.GspException
import org.grox.view.gsp.GspRenderContext
import org.grox.web.RequestContext

/**
 * Bean {@code groovyPageRenderer}: renderiza uma view ou template GSP para String, dentro
 * ou fora de uma requisição (ex.: corpo de e-mail em um job).
 * <pre>
 * groovyPageRenderer.render(template: '/mail/aviso', model: [conta: c])
 * groovyPageRenderer.render(view: '/mail/aviso', model: [conta: c])
 * </pre>
 * Sem requisição, caminhos devem ser absolutos (começando com {@code /}).
 */
class PageRenderer {

    private final GroxRuntime runtime

    PageRenderer(GroxRuntime runtime) {
        this.runtime = runtime
    }

    private GspViews getViews() { runtime.module(org.grox.view.GspModule).views }

    String render(Map args) {
        def sw = new StringWriter()
        def rc = new GspRenderContext(RequestContext.currentOrNull(), views, sw)
        Map model = (Map) (args.model ?: [:])
        rc.bind {
            if (args.template) {
                String path = GspViews.templatePath(args.template.toString(), rc)
                return views.renderTemplate(path, args, rc).toString()
            }
            String view = args.view?.toString()
            if (!view) throw new IllegalArgumentException('groovyPageRenderer.render: informe view: ou template:')
            def page = views.engine.find(view)
            def other = page == null ? views.engineFor(view) : null   // Pug, Markdown...
            if (page == null && other == null) throw new GspException(null, 0, "view não encontrada: ${view}.gsp")
            if (page != null) views.engine.render(page, model, rc)
            else other.render(view, model, rc)
            sw.toString()
        }
    }

    void renderTo(Map args, Writer out) {
        out.write(render(args))
    }
}
