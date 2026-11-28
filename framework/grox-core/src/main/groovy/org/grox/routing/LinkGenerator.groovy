package org.grox.routing

import org.grox.core.ClassUtils

/** Equivalente ao {@code createLink} do Grails. */
@groovy.transform.CompileStatic
class LinkGenerator {

    final UrlMappingsHolder holder
    final Map<String, ? extends RouteTarget> controllers
    final String serverURL

    LinkGenerator(UrlMappingsHolder holder, Map<String, ? extends RouteTarget> controllers, String serverURL) {
        this.holder = holder
        this.controllers = controllers
        this.serverURL = serverURL
    }

    String link(Map attrs, String currentController = null) {
        Object url = attrs.get('url')
        if (url instanceof Map) return link((Map) url, currentController)
        if (url != null && !url.toString().isEmpty()) return url.toString()
        Object b = attrs.get('base')
        String base = b != null && !b.toString().isEmpty() ? b.toString() : (attrs.get('absolute') ? (serverURL ?: '') : '')
        Object f = attrs.get('fragment')
        String fragment = f != null && !f.toString().isEmpty() ? '#' + f : ''
        Object uri = attrs.get('uri')
        if (uri != null && !uri.toString().isEmpty()) return base + uri + fragment

        Object c = attrs.get('controller')
        String controller = c != null && !c.toString().isEmpty() ? c.toString() : currentController
        Object a = attrs.get('action')
        String action = a?.toString()
        Map<String, Object> params = new LinkedHashMap<>()
        Object p = attrs.get('params')
        if (p instanceof Map) params.putAll((Map<String, Object>) p)
        if (attrs.get('id') != null) params.put('id', attrs.get('id'))
        // resource: 'livro' ou uma instância (controller pelo nome da classe e id da instância), como no Grails
        Object resource = attrs.get('resource')
        if (resource instanceof CharSequence) {
            controller = resource.toString()
        } else if (resource != null) {
            controller = ClassUtils.propertyName(resource.getClass())
            if (params.get('id') == null) params.put('id', resource.hasProperty('id') ? org.codehaus.groovy.runtime.InvokerHelper.getProperty(resource, 'id') : null)
            if (action == null && params.get('id') != null) action = 'show'
        }

        RouteTarget alvo = controller != null ? controllers.get(controller) : null
        UrlMappingsHolder.Reverso r = holder.reverse(controller, action, params, alvo?.defaultAction)
        StringBuilder sb = new StringBuilder(base).append(r.path)
        boolean primeiro = true
        for (Map.Entry<String, Object> e : params.entrySet()) {
            Object v = e.value
            if (v == null || r.used.contains(e.key)) continue
            String k = UrlMapping.encode(String.valueOf(e.key))
            if (v instanceof Collection) {
                for (Object item : (Collection) v) {
                    sb.append(primeiro ? (char) '?' : (char) '&').append(k).append((char) '=').append(UrlMapping.encode(String.valueOf(item)))
                    primeiro = false
                }
            } else {
                sb.append(primeiro ? (char) '?' : (char) '&').append(k).append((char) '=').append(UrlMapping.encode(v.toString()))
                primeiro = false
            }
        }
        sb.append(fragment).toString()
    }
}
