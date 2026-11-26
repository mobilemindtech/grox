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
        if (attrs.url instanceof Map) return link((Map) attrs.url, currentController)
        if (attrs.url) return attrs.url.toString()
        String base = attrs.base?.toString() ?: (attrs.absolute ? (serverURL ?: '') : '')
        String fragment = attrs.fragment ? "#${attrs.fragment}" : ''
        if (attrs.uri) return base + attrs.uri + fragment

        String controller = (attrs.controller ?: currentController)?.toString()
        String action = attrs.action?.toString()
        Map<String, Object> params = [:]
        if (attrs.params instanceof Map) params.putAll((Map) attrs.params)
        if (attrs.id != null) params.id = attrs.id
        // resource: 'livro' ou uma instância (controller pelo nome da classe e id da instância), como no Grails
        Object resource = attrs.resource
        if (resource instanceof CharSequence) {
            controller = resource.toString()
        } else if (resource != null) {
            controller = ClassUtils.propertyName(resource.getClass())
            if (params.id == null) params.id = resource.hasProperty('id') ? org.codehaus.groovy.runtime.InvokerHelper.getProperty(resource, 'id') : null
            if (action == null && params.id != null) action = 'show'
        }

        def (String path, Set<String> used) = holder.reverse(controller, action, params, controllers[controller]?.defaultAction)
        Map query = params.findAll { k, v -> !(k in used) && v != null }
        String qs = query.collect { k, v ->
            (v instanceof Collection ? v : [v]).collect { "${UrlMapping.encode(k.toString())}=${UrlMapping.encode(it.toString())}" }.join('&')
        }.join('&')
        base + path + (qs ? "?$qs" : '') + fragment
    }
}
