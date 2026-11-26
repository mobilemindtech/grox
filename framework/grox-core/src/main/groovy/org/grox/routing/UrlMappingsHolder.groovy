package org.grox.routing

import org.grox.core.ClassUtils

import static org.grox.routing.UrlMapping.MARK

/** Conjunto ordenado de mapeamentos: resolve rotas e gera URLs reversas. */
@groovy.transform.CompileStatic
class UrlMappingsHolder {

    final List<UrlMapping> mappings
    final Map<Integer, UrlMapping> errorMappings

    UrlMappingsHolder(List<UrlMapping> all) {
        errorMappings = all.findAll { it.statusCode != null }.collectEntries { [(it.statusCode): it] }
        mappings = all.findAll { it.statusCode == null }.toSorted { UrlMapping a, UrlMapping b ->
            b.literalWeight <=> a.literalWeight ?: a.varNames.size() <=> b.varNames.size() ?: a.order <=> b.order
        }
    }

    static UrlMappingsHolder build(List<Class<?>> urlMappingsClasses) {
        def builder = new UrlMappingsBuilder()
        for (Class<?> c : urlMappingsClasses) {
            Object mappings = ClassUtils.staticProperty(c, 'mappings')
            if (mappings instanceof Closure) {
                Closure cl = (Closure) ((Closure) mappings).clone()
                cl.delegate = builder
                cl.resolveStrategy = Closure.DELEGATE_FIRST
                builder.owner = c
                cl.call()
            }
        }
        if (!builder.mappings) {
            builder.add("/${MARK}controller${MARK}/${MARK}action${MARK}?/${MARK}id${MARK}?(.${MARK}format${MARK})?", [:], null)
        }
        new UrlMappingsHolder(builder.mappings)
    }

    RouteMatch match(String path, String method, Map<String, ? extends RouteTarget> controllers) {
        String verb = method == 'HEAD' ? 'GET' : method
        boolean methodMismatch = false
        for (UrlMapping m : mappings) {
            Map<String, String> vars = m.match(path)
            if (vars == null) continue
            if (m.httpMethod && m.httpMethod != verb) {
                methodMismatch = true
                continue
            }
            if (m.attrs.redirect) return new RouteMatch(mapping: m, redirect: m.attrs.redirect.toString())
            String controllerName = vars.controller ?: m.attrs.controller?.toString()
            if (m.attrs.view && !controllerName) {
                return new RouteMatch(mapping: m, view: m.attrs.view.toString(), params: paramsOf(m, vars))
            }
            RouteTarget artefact = controllers[controllerName]
            if (artefact == null) continue
            Object act = m.attrs.action
            String actionName = vars.action
            if (!actionName && act instanceof Map) {
                actionName = ((Map) act)[verb]?.toString()
                if (!actionName) {
                    methodMismatch = true
                    continue
                }
            }
            actionName = actionName ?: act?.toString() ?: artefact.defaultAction
            if (!actionName || !artefact.hasAction(actionName)) continue
            return new RouteMatch(mapping: m, controller: artefact, actionName: actionName, params: paramsOf(m, vars))
        }
        methodMismatch ? new RouteMatch(methodNotAllowed: true) : null
    }

    private static Map<String, Object> paramsOf(UrlMapping m, Map<String, String> vars) {
        Map<String, Object> p = [:]
        if (m.attrs.params instanceof Map) p.putAll((Map) m.attrs.params)
        vars.each { k, v -> if (k != 'controller' && k != 'action') p[k] = v }
        if (m.attrs.id != null && p.id == null) p.id = m.attrs.id
        p
    }

    UrlMapping errorMapping(int status) {
        errorMappings[status]
    }

    /**
     * Caminho para controller/action. Prefere mapeamentos estáticos
     * ({@code "/usuarios/$id"(controller: 'usuario', action: 'show')}), depois os
     * dinâmicos ({@code /$controller/$action}).
     * @return [caminho, nomes de parâmetros consumidos pelo caminho]
     */
    List reverse(String controller, String action, Map<String, Object> params, String defaultAction) {
        String effectiveAction = action ?: defaultAction
        Map<String, Object> values = new LinkedHashMap<>(params)
        values.controller = controller
        if (action || params.id != null) values.action = effectiveAction

        for (UrlMapping m : mappings) {
            if (m.attrs.redirect || m.attrs.controller?.toString() != controller) continue
            // mapeamentos que fixam parâmetros (ex. format: 'json') só servem se o link pedir o mesmo valor
            Map fixed = (Map) m.attrs.params
            if (fixed && fixed.any { k, v -> params[k]?.toString() != v?.toString() }) continue
            Object a = m.attrs.action
            boolean ok
            if (a instanceof Map) ok = effectiveAction in ((Map) a).values()*.toString()
            else if (a != null) ok = a.toString() == effectiveAction
            else ok = m.varNames.contains('action') || !action || action == defaultAction
            if (!ok) continue
            Set<String> used = [] as Set
            String url = m.reverse(values, used)
            if (url) return [url, used]
        }
        for (UrlMapping m : mappings) {
            if (!m.varNames.contains('controller')) continue
            Set<String> used = [] as Set
            String url = m.reverse(values, used)
            if (url) return [url, used]
        }
        Set<String> used = ['id'] as Set
        String url = "/${UrlMapping.encode(controller.toString())}" + (values.action ? "/${UrlMapping.encode(values.action.toString())}" : '') +
                (params.id != null ? "/${UrlMapping.encode(params.id.toString())}" : '')
        [url, used]
    }
}
