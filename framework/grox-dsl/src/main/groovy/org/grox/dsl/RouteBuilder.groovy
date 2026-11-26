package org.grox.dsl

/**
 * Delegate do closure {@code routes} de uma classe {@link Routes}: {@code get}, {@code post}, {@code put},
 * {@code patch}, {@code delete} e {@code group} (ou {@code ns}). Opções (antes do caminho): {@code name}, {@code view},
 * {@code execution} ({@code virtual}/{@code actor}) e {@code poolSize}; as de um grupo valem para as rotas dele.
 * Sem caminho ({@code post { }}), a rota atende o caminho do grupo. {@code before}, {@code after} e {@code error}
 * ({@link Interceptor}) valem para as rotas do escopo onde são declarados (a classe ou o grupo).
 */
class RouteBuilder {

    final RouteGroup group
    final List<Route> routes = []
    /** Classes {@link Handler} declaradas com {@code handlers X, Y} (DSL de projeto). */
    final List<Class<?>> handlers = []
    /** Escopo da raiz (a classe de rotas ou, na DSL de projeto, a aplicação inteira). */
    final Scope root = new Scope(null)
    private Scope scope = root
    private String prefix = ''
    private Map<String, Object> options = [:]

    RouteBuilder(RouteGroup group) {
        this.group = group
    }

    Route get(String path, Closure handler) { route('GET', [:], path, handler) }

    Route get(Map opts, String path, Closure handler) { route('GET', opts, path, handler) }

    Route get(Closure handler) { route('GET', [:], '', handler) }

    Route get(Map opts, Closure handler) { route('GET', opts, '', handler) }

    Route post(String path, Closure handler) { route('POST', [:], path, handler) }

    Route post(Map opts, String path, Closure handler) { route('POST', opts, path, handler) }

    Route post(Closure handler) { route('POST', [:], '', handler) }

    Route post(Map opts, Closure handler) { route('POST', opts, '', handler) }

    Route put(String path, Closure handler) { route('PUT', [:], path, handler) }

    Route put(Map opts, String path, Closure handler) { route('PUT', opts, path, handler) }

    Route put(Closure handler) { route('PUT', [:], '', handler) }

    Route put(Map opts, Closure handler) { route('PUT', opts, '', handler) }

    Route patch(String path, Closure handler) { route('PATCH', [:], path, handler) }

    Route patch(Map opts, String path, Closure handler) { route('PATCH', opts, path, handler) }

    Route patch(Closure handler) { route('PATCH', [:], '', handler) }

    Route patch(Map opts, Closure handler) { route('PATCH', opts, '', handler) }

    Route delete(String path, Closure handler) { route('DELETE', [:], path, handler) }

    Route delete(Map opts, String path, Closure handler) { route('DELETE', opts, path, handler) }

    Route delete(Closure handler) { route('DELETE', [:], '', handler) }

    Route delete(Map opts, Closure handler) { route('DELETE', opts, '', handler) }

    /** {@code handlers LivrosApi, AutoresApi}: classes {@link Handler} da aplicação. */
    void handlers(Class<?>... classes) {
        handlers.addAll(classes)
    }

    /** Rotas com um prefixo de caminho e opções comuns ({@code group('/relatorios', execution: 'actor') { ... }}). */
    void group(String path, Closure body) { group([:], path, body) }

    void group(Map opts, String path, Closure body) {
        String savedPrefix = prefix
        Map<String, Object> savedOptions = options
        Scope savedScope = scope
        prefix = join(prefix, path)
        options = options + opts
        scope = new Scope(scope)
        try {
            Closure c = (Closure) body.clone()
            c.delegate = this
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
        } finally {
            prefix = savedPrefix
            options = savedOptions
            scope = savedScope
        }
    }

    /** {@code ns('/usuarios') { ... }}: o mesmo que {@link #group}. */
    void ns(String path, Closure body) { group([:], path, body) }

    void ns(Map opts, String path, Closure body) { group(opts, path, body) }

    /** Antes das rotas do escopo; {@code false} ou uma resposta interrompe a rota. */
    void before(Closure body) { intercept(Interceptor.BEFORE, null, body) }

    /** Antes das rotas do escopo cujo caminho casa com o padrão Ant ({@code before('/api/**') { }}). */
    void before(String path, Closure body) { intercept(Interceptor.BEFORE, path, body) }

    /** Depois das rotas do escopo, com o valor devolvido ({@code after { model -> }}). */
    void after(Closure body) { intercept(Interceptor.AFTER, null, body) }

    void after(String path, Closure body) { intercept(Interceptor.AFTER, path, body) }

    /** Exceção numa rota do escopo ({@code error { Throwable e -> render status: 500, text: e.message }}). */
    void error(Closure body) { intercept(Interceptor.ERROR, null, body) }

    void error(String path, Closure body) { intercept(Interceptor.ERROR, path, body) }

    private void intercept(String kind, String path, Closure body) {
        scope.interceptors << new Interceptor(kind, path, body)
    }

    Route route(String httpMethod, Map opts, String path, Closure handler) {
        Map<String, Object> o = options + opts
        def r = new Route(group: group, httpMethod: httpMethod, path: join(prefix, path), closure: handler,
                action: o.name?.toString() ?: "r${routes.size()}".toString(), view: o.view?.toString(),
                execution: o.execution?.toString(), poolSize: (o.poolSize ?: 0) as int, access: accessOf(o.access), scope: scope)
        routes << r
        r
    }

    static List<String> accessOf(Object access) {
        if (access == null) return null
        List<String> list = (access instanceof Collection || access.getClass().array ? access as List : [access])*.toString()
        list ?: null
    }

    /** Prefixo + caminho, com uma barra entre eles ({@code /user} + {@code {id}} = {@code /user/{id}}). */
    static String join(String a, String b) {
        a = a ?: ''
        b = b == '/' && a ? '' : (b ?: '')
        String s = a && b && !a.endsWith('/') && !b.startsWith('/') ? a + '/' + b : a + b
        s.startsWith('/') ? s : '/' + s
    }

    /** Escopo de declaração (a raiz ou um grupo) e os interceptores dele. */
    static class Scope {
        final Scope parent
        final List<Interceptor> interceptors = []

        Scope(Scope parent) {
            this.parent = parent
        }

        /** Os interceptores deste escopo e dos de fora, do mais externo para o mais interno. */
        List<Interceptor> chain() {
            List<Interceptor> result = parent?.chain() ?: []
            result.addAll(interceptors)
            result
        }
    }
}
