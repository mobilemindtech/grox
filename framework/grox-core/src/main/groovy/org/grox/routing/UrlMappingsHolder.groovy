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

    /**
     * O primeiro mapeamento que casa o caminho e leva a um destino (redirect, view ou controller e action existentes).
     * Um mapeamento de outro verbo só conta para o 405 se levaria a um destino: {@code delete "/$controller/$id"}
     * não faz um GET em {@code /nao-existe} (controller inexistente) dar 405 em vez de 404.
     */
    RouteMatch match(String path, String method, Map<String, ? extends RouteTarget> controllers) {
        String verb = method == 'HEAD' ? 'GET' : method
        boolean methodMismatch = false
        for (UrlMapping m : mappings) {
            Map<String, String> vars = m.match(path)
            if (vars == null) continue
            boolean verbOk = !m.httpMethod || m.httpMethod == verb
            RouteMatch found = resolve(m, vars, verb, controllers)
            if (found == null) continue
            if (!verbOk || found.methodNotAllowed) {
                methodMismatch = true
                continue
            }
            return found
        }
        methodMismatch ? new RouteMatch(methodNotAllowed: true) : null
    }

    /** O destino do mapeamento, null se não há (controller ou action inexistente), ou um 405 (action só de outro verbo). */
    private static RouteMatch resolve(UrlMapping m, Map<String, String> vars, String verb, Map<String, ? extends RouteTarget> controllers) {
        if (m.attrs.redirect) return new RouteMatch(mapping: m, redirect: m.attrs.redirect.toString())
        String controllerName = vars.controller ?: m.attrs.controller?.toString()
        if (m.attrs.view && !controllerName) {
            return new RouteMatch(mapping: m, view: m.attrs.view.toString(), params: paramsOf(m, vars))
        }
        RouteTarget artefact = controllers[controllerName]
        if (artefact == null) return null
        Object act = m.attrs.action
        String actionName = vars.action
        if (!actionName && act instanceof Map) {
            actionName = ((Map) act)[verb]?.toString()
            if (!actionName) return new RouteMatch(methodNotAllowed: true)
        }
        actionName = actionName ?: act?.toString() ?: artefact.defaultAction
        if (!actionName || !artefact.hasAction(actionName)) return null
        new RouteMatch(mapping: m, controller: artefact, actionName: actionName, params: paramsOf(m, vars))
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
