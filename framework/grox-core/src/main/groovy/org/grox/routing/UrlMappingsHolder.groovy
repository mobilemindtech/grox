package org.grox.routing

import org.grox.core.ClassUtils

import static org.grox.routing.UrlMapping.MARK

/** Conjunto ordenado de mapeamentos: resolve rotas e gera URLs reversas. */
@groovy.transform.CompileStatic
class UrlMappingsHolder {

    final List<UrlMapping> mappings
    final Map<Integer, UrlMapping> errorMappings
    /**
     * Índice do {@link #match}: pelo primeiro segmento do caminho, os mapeamentos que começam com esse literal mais
     * os que não começam com literal, na ordem de {@link #mappings}. Caminho com outro primeiro segmento: só
     * {@link #semLiteral}.
     */
    private final Map<String, List<UrlMapping>> porPrimeiroSegmento = new HashMap<>()
    private final List<UrlMapping> semLiteral = []
    /** Índice do {@link #reverse}: por {@code controller:} fixo (null nos que não fixam), sem os de redirect. */
    private final Map<String, List<UrlMapping>> porController = new HashMap<>()
    /** Os que têm {@code $controller} no caminho (o reverso genérico). */
    private final List<UrlMapping> comVarController = []

    UrlMappingsHolder(List<UrlMapping> all) {
        errorMappings = all.findAll { it.statusCode != null }.collectEntries { [(it.statusCode): it] }
        mappings = all.findAll { it.statusCode == null }.toSorted { UrlMapping a, UrlMapping b ->
            b.literalWeight <=> a.literalWeight ?: a.varNames.size() <=> b.varNames.size() ?: a.order <=> b.order
        }
        Set<String> literais = new HashSet<>()
        for (UrlMapping m : mappings) {
            String l = m.firstLiteral
            if (l != null) literais.add(l)
        }
        for (String l : literais) porPrimeiroSegmento.put(l, new ArrayList<UrlMapping>())
        for (UrlMapping m : mappings) {
            String l = m.firstLiteral
            if (l != null) {
                porPrimeiroSegmento.get(l).add(m)
            } else {
                semLiteral.add(m)
                for (List<UrlMapping> lista : porPrimeiroSegmento.values()) lista.add(m)
            }
            if (!m.attrs.get('redirect')) {
                Object c = m.attrs.get('controller')
                String chave = c?.toString()
                List<UrlMapping> lista = porController.get(chave)
                if (lista == null) porController.put(chave, lista = new ArrayList<UrlMapping>())
                lista.add(m)
            }
            if (m.varNames.contains('controller')) comVarController.add(m)
        }
    }

    /** Os mapeamentos que podem casar o caminho, na ordem de {@link #mappings}. */
    private List<UrlMapping> candidatos(String path) {
        if (porPrimeiroSegmento.isEmpty() || path.length() < 2 || path.charAt(0) != (char) '/') return semLiteral
        int fim = path.indexOf('/', 1)
        List<UrlMapping> lista = porPrimeiroSegmento.get(fim < 0 ? path.substring(1) : path.substring(1, fim))
        lista != null ? lista : semLiteral
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
        for (UrlMapping m : candidatos(path)) {
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

    /** Caminho gerado pelo {@link #reverse} e os parâmetros que ele consumiu (o resto vai na query string). */
    static final class Reverso {
        final String path
        final Set<String> used

        Reverso(String path, Set<String> used) {
            this.path = path
            this.used = used
        }
    }

    /**
     * Caminho para controller/action. Prefere mapeamentos estáticos
     * ({@code "/usuarios/$id"(controller: 'usuario', action: 'show')}), depois os
     * dinâmicos ({@code /$controller/$action}).
     */
    Reverso reverse(String controller, String action, Map<String, Object> params, String defaultAction) {
        String effectiveAction = action ?: defaultAction
        Map<String, Object> values = new LinkedHashMap<>(params)
        values.put('controller', controller)
        if ((action != null && !action.isEmpty()) || params.get('id') != null) values.put('action', effectiveAction)

        List<UrlMapping> estaticos = porController.get(controller)
        if (estaticos != null) {
            for (UrlMapping m : estaticos) {
                if (!aceita(m, params, action, effectiveAction, defaultAction)) continue
                Set<String> used = new HashSet<>()
                String url = m.reverse(values, used)
                if (url != null && !url.isEmpty()) return new Reverso(url, used)
            }
        }
        for (UrlMapping m : comVarController) {
            Set<String> used = new HashSet<>()
            String url = m.reverse(values, used)
            if (url != null && !url.isEmpty()) return new Reverso(url, used)
        }
        StringBuilder url = new StringBuilder('/').append(UrlMapping.encode(String.valueOf(controller)))
        Object a = values.get('action')
        if (a != null && !a.toString().isEmpty()) url.append((char) '/').append(UrlMapping.encode(a.toString()))
        Object id = params.get('id')
        if (id != null) url.append((char) '/').append(UrlMapping.encode(id.toString()))
        new Reverso(url.toString(), Collections.singleton('id'))
    }

    /** O mapeamento estático serve para o link: mesmos parâmetros fixos e a action pedida. */
    private static boolean aceita(UrlMapping m, Map<String, Object> params, String action, String effectiveAction, String defaultAction) {
        // mapeamentos que fixam parâmetros (ex. format: 'json') só servem se o link pedir o mesmo valor
        Object fixos = m.attrs.get('params')
        if (fixos instanceof Map) {
            for (Map.Entry e : ((Map) fixos).entrySet()) {
                if (!String.valueOf(params.get(e.key)).equals(String.valueOf(e.value))) return false
            }
        }
        Object a = m.attrs.get('action')
        if (a instanceof Map) {
            for (Object v : ((Map) a).values()) {
                if (String.valueOf(v).equals(effectiveAction)) return true
            }
            return false
        }
        if (a != null) return a.toString().equals(effectiveAction)
        m.varNames.contains('action') || action == null || action.isEmpty() || Objects.equals(action, defaultAction)
    }
}
