package org.grox.view.taglib

import org.grox.view.GspViews
import org.grox.view.ViewRequest
import org.grox.http.GroxParams
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
    @groovy.transform.CompileStatic
    void paginate(Map attrs) {
        if (attrs.get('total') == null) throw new IllegalArgumentException('<g:paginate> exige o atributo total')
        long total = attrs.get('total') as long
        GroxParams p = params
        Integer maxParam = p.int('max')
        int max = maxParam ?: (attrs.get('max') ?: 10) as int
        if (max <= 0) max = 10
        Long offsetParam = p.long('offset')
        long offset = offsetParam ?: (attrs.get('offset') ?: 0) as long
        int maxsteps = (attrs.get('maxsteps') ?: 10) as int
        Map linkParams = new LinkedHashMap((Map) (attrs.get('params') ?: [:]))
        if (p.get('sort')) linkParams.put('sort', p.get('sort'))
        if (p.get('order')) linkParams.put('order', p.get('order'))
        Map base = [controller: attrs.get('controller') ?: controllerName, action: attrs.get('action') ?: actionName]
        if (attrs.get('id') != null) base.put('id', attrs.get('id'))
        ViewRequest wr = webRequest
        String controller = controllerName

        long current = offset.intdiv(max) + 1
        long last = Math.max(1L, Math.ceil(total / (double) max) as long)
        StringBuilder sb = new StringBuilder()
        if (current > 1 && !attrs.get('omitPrev')) {
            sb.append('<a href="').append(pageHref(wr, controller, base, linkParams, (current - 2) * max, max)).append('" class="prevLink">')
                    .append(pageLabel(wr, 'paginate.prev', attrs.get('prev'))).append('</a>')
        }
        if (maxsteps > 0 && last > 1) {
            long begin = Math.max(1L, current - (long) (maxsteps / 2))
            long end = Math.min(last, begin + maxsteps - 1)
            begin = Math.max(1L, end - maxsteps + 1)
            if (begin > 1 && !attrs.get('omitFirst')) {
                sb.append('<a href="').append(pageHref(wr, controller, base, linkParams, 0, max)).append('" class="step">1</a>')
                if (begin > 2) sb.append('<span class="step gap">..</span>')
            }
            for (long i = begin; i <= end; i++) {
                if (i == current) sb.append('<span class="currentStep">').append(i).append('</span>')
                else sb.append('<a href="').append(pageHref(wr, controller, base, linkParams, (i - 1) * max, max)).append('" class="step">').append(i).append('</a>')
            }
            if (end < last && !attrs.get('omitLast')) {
                if (end < last - 1) sb.append('<span class="step gap">..</span>')
                sb.append('<a href="').append(pageHref(wr, controller, base, linkParams, (last - 1) * max, max)).append('" class="step">').append(last).append('</a>')
            }
        }
        if (current < last && !attrs.get('omitNext')) {
            sb.append('<a href="').append(pageHref(wr, controller, base, linkParams, current * max, max)).append('" class="nextLink">')
                    .append(pageLabel(wr, 'paginate.next', attrs.get('next'))).append('</a>')
        }
        out.write(sb.toString())
    }

    @groovy.transform.CompileStatic
    private static String pageHref(ViewRequest wr, String controller, Map base, Map linkParams, long offset, int max) {
        Map params = new LinkedHashMap(linkParams)
        params.put('offset', offset)
        params.put('max', max)
        Map link = new LinkedHashMap(base)
        link.put('params', params)
        Html.escape(wr.links.link(link, controller))
    }

    @groovy.transform.CompileStatic
    private static String pageLabel(ViewRequest wr, String key, Object given) {
        Html.escape(given ?: wr.messages.getMessage(key, null, null, wr.locale))
    }

    /** {@code <g:sortableColumn property="nome" title="Nome"/>}: {@code <th class="sortable sorted asc"><a ...>}. */
    @groovy.transform.CompileStatic
    void sortableColumn(Map attrs) {
        String property = attrs.remove('property')?.toString()
        if (!property) throw new IllegalArgumentException('<g:sortableColumn> exige o atributo property')
        ViewRequest wr = webRequest
        GroxParams p = params
        Object titleKey = attrs.remove('titleKey')
        String title = attrs.remove('title')?.toString() ?:
                (titleKey ? wr.messages.getMessage(titleKey.toString(), null, property, wr.locale) : property)
        String defaultOrder = attrs.remove('defaultOrder')?.toString() == 'desc' ? 'desc' : 'asc'
        String order = p.get('order') == 'desc' ? 'desc' : 'asc'
        String css = 'sortable'
        String newOrder = defaultOrder
        if (p.get('sort') == property) {
            css += ' sorted ' + order
            newOrder = order == 'asc' ? 'desc' : 'asc'
        }
        Map linkParams = new LinkedHashMap((Map) (attrs.remove('params') ?: [:]))
        if (p.get('max')) linkParams.put('max', p.get('max'))
        linkParams.put('sort', property)
        linkParams.put('order', newOrder)
        Object controller = attrs.remove('controller') ?: controllerName
        Object action = attrs.remove('action') ?: actionName
        String href = wr.links.link([controller: controller, action: action, params: linkParams], controllerName)
        out.write('<th class="' + css + '"' + Html.attributes(attrs) + '><a href="' + Html.escape(href) + '">' + Html.escape(title) + '</a></th>')
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
