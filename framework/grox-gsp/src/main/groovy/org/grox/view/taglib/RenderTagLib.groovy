package org.grox.view.taglib

import org.grox.view.GspViews
import org.grox.http.Html
import org.grox.view.layout.SiteMeshLayouts

/** Tags {@code g:} de templates e layouts. */
class RenderTagLib implements TagLibApi {

    /** {@code <g:render template="form" model="${[u: u]}" collection="${lista}" var="item"/>} */
    def render = { Map attrs ->
        if (!attrs.template) throw new IllegalArgumentException('<g:render> exige o atributo template')
        String path = GspViews.templatePath(attrs.template.toString(), renderContext)
        out << renderContext.views.renderTemplate(path, attrs, renderContext)
    }

    /**
     * {@code <g:paginate total="${n}" max="10" maxsteps="10" params="${[q: x]}"/>}: links
     * {@code prevLink}, {@code step}, {@code currentStep}, {@code nextLink} (marcação do Grails),
     * preservando sort/order.
     */
    def paginate = { Map attrs ->
        if (attrs.total == null) throw new IllegalArgumentException('<g:paginate> exige o atributo total')
        long total = attrs.total as long
        int max = params.int('max') ?: (attrs.max ?: 10) as int
        if (max <= 0) max = 10
        long offset = params.long('offset') ?: (attrs.offset ?: 0) as long
        int maxsteps = (attrs.maxsteps ?: 10) as int
        Map linkParams = new LinkedHashMap((Map) (attrs.params ?: [:]))
        if (params.sort) linkParams.sort = params.sort
        if (params.order) linkParams.order = params.order
        Map base = [controller: attrs.controller ?: controllerName, action: attrs.action ?: actionName]
        if (attrs.id != null) base.id = attrs.id
        Closure<String> href = { long o -> Html.escape(createLink(base + [params: linkParams + [offset: o, max: max]])) }
        Closure<String> label = { String key, Object given -> Html.escape(given ?: webRequest.messages.getMessage(key, null, null, webRequest.locale)) }

        long current = offset.intdiv(max) + 1
        long last = Math.max(1, Math.ceil(total / (double) max) as long)
        StringBuilder sb = new StringBuilder()
        if (current > 1 && !attrs.omitPrev) sb << "<a href=\"${href((current - 2) * max)}\" class=\"prevLink\">${label('paginate.prev', attrs.prev)}</a>"
        if (maxsteps > 0 && last > 1) {
            long begin = Math.max(1, current - (maxsteps / 2 as long))
            long end = Math.min(last, begin + maxsteps - 1)
            begin = Math.max(1, end - maxsteps + 1)
            if (begin > 1 && !attrs.omitFirst) {
                sb << "<a href=\"${href(0)}\" class=\"step\">1</a>"
                if (begin > 2) sb << '<span class="step gap">..</span>'
            }
            for (long i = begin; i <= end; i++) {
                if (i == current) sb << "<span class=\"currentStep\">${i}</span>"
                else sb << "<a href=\"${href((i - 1) * max)}\" class=\"step\">${i}</a>"
            }
            if (end < last && !attrs.omitLast) {
                if (end < last - 1) sb << '<span class="step gap">..</span>'
                sb << "<a href=\"${href((last - 1) * max)}\" class=\"step\">${last}</a>"
            }
        }
        if (current < last && !attrs.omitNext) sb << "<a href=\"${href(current * max)}\" class=\"nextLink\">${label('paginate.next', attrs.next)}</a>"
        out << sb
    }

    /** {@code <g:sortableColumn property="nome" title="Nome"/>}: {@code <th class="sortable sorted asc"><a ...>}. */
    def sortableColumn = { Map attrs ->
        String property = attrs.remove('property')?.toString()
        if (!property) throw new IllegalArgumentException('<g:sortableColumn> exige o atributo property')
        Object titleKey = attrs.remove('titleKey')
        String title = attrs.remove('title')?.toString() ?:
                (titleKey ? webRequest.messages.getMessage(titleKey.toString(), null, property, webRequest.locale) : property)
        String defaultOrder = attrs.remove('defaultOrder')?.toString() == 'desc' ? 'desc' : 'asc'
        String order = params.order == 'desc' ? 'desc' : 'asc'
        String css = 'sortable'
        String newOrder = defaultOrder
        if (params.sort == property) {
            css += " sorted ${order}"
            newOrder = order == 'asc' ? 'desc' : 'asc'
        }
        Map linkParams = new LinkedHashMap((Map) (attrs.remove('params') ?: [:]))
        if (params.max) linkParams.max = params.max
        linkParams.sort = property
        linkParams.order = newOrder
        String href = createLink([controller: attrs.remove('controller') ?: controllerName, action: attrs.remove('action') ?: actionName, params: linkParams])
        out << "<th class=\"${css}\"${Html.attributes(attrs)}><a href=\"${Html.escape(href)}\">${Html.escape(title)}</a></th>"
    }

    def layoutTitle = { Map attrs ->
        out << (SiteMeshLayouts.property(renderContext.layoutContent, 'title') ?: attrs.default ?: '')
    }

    def layoutHead = { Map attrs ->
        out << (SiteMeshLayouts.property(renderContext.layoutContent, 'head') ?: '')
    }

    def layoutBody = { Map attrs ->
        out << (SiteMeshLayouts.property(renderContext.layoutContent, 'body') ?: '')
    }

    /** {@code <g:pageProperty name="page.menu"/>}, {@code name="meta.description"}, {@code name="body.onload"}. */
    def pageProperty = { Map attrs ->
        out << (SiteMeshLayouts.property(renderContext.layoutContent, attrs.name.toString()) ?: attrs.default ?: '')
    }

    def ifPageProperty = { Map attrs, body ->
        if (SiteMeshLayouts.property(renderContext.layoutContent, attrs.name.toString())) out << body()
    }

    /** {@code <g:applyLayout name="bloco">conteúdo</g:applyLayout>} */
    def applyLayout = { Map attrs, body ->
        String html = attrs.template ?
                renderContext.views.renderTemplate(GspViews.templatePath(attrs.template.toString(), renderContext), attrs, renderContext).toString() :
                body().toString()
        out << renderContext.views.layouts.applyLayout(attrs.name.toString(), html, (Map) (attrs.model ?: [:]), renderContext)
    }
}
