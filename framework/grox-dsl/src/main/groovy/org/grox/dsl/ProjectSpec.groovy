package org.grox.dsl

/**
 * A aplicação declarada pela DSL de projeto ({@link ProjectDsl}): configuração padrão, segurança, rotas e beans.
 * <pre>
 * class Livraria {
 *     static app = {
 *         config { grox.routes.execution = 'virtual' }
 *         security {
 *             user 'admin', password: '{noop}admin', roles: ['ROLE_ADMIN']
 *             rule '/api/**', 'ROLE_ADMIN'
 *         }
 *         routes {
 *             get('/livros', access: 'permitAll') { render view: 'livros/index' }
 *             handlers LivrosApi
 *         }
 *         beans { relogio = java.time.Clock.systemUTC() }
 *         bootstrap {
 *             init { if (!livroService.todos()) livroService.salvar(titulo: 'Dom Casmurro') }
 *             destroy { log.info 'fim' }
 *         }
 *     }
 * }
 * </pre>
 */
class ProjectSpec {

    /** {@code config { }} e o {@code security { }} traduzido, como valores padrão da configuração. */
    final Map<String, Object> config = [:]
    /** Closures {@code routes { }} (delegate: {@link RouteBuilder}). */
    final List<Closure> routes = []
    /** Classes {@link Handler} declaradas em {@code routes { handlers X, Y }}. */
    final List<Class<?>> handlers = []
    /** Closures {@code beans { }}, no formato do {@code resources.groovy}. */
    final List<Closure> beans = []
    /** {@code bootstrap { init { } }}: na subida, depois dos services e antes do servidor. */
    final List<Closure> inits = []
    /** {@code bootstrap { destroy { } }}: no desligamento. */
    final List<Closure> destroys = []

    void config(Closure body) {
        run(body, new ConfigBlock(config))
    }

    void security(Closure body) {
        Map<String, Object> springSecurity = nested(nested(nested(config, 'grails'), 'plugin'), 'springsecurity')
        Map<String, Object> groxSecurity = nested(nested(config, 'grox'), 'security')
        List<Map> users = (List<Map>) groxSecurity.computeIfAbsent('users') { [] }
        List<Map> rules = (List<Map>) groxSecurity.computeIfAbsent('rules') { [] }
        run(body, new SecurityBlock(springSecurity, users, rules))
    }

    void routes(Closure body) {
        routes << body
    }

    void beans(Closure body) {
        beans << body
    }

    void bootstrap(Closure body) {
        run(body, new BootStrapBlock(inits, destroys))
    }

    private static Map<String, Object> nested(Map<String, Object> m, String key) {
        (Map<String, Object>) m.computeIfAbsent(key) { new LinkedHashMap<String, Object>() }
    }

    private static void run(Closure body, Object delegate) {
        Closure c = (Closure) body.clone()
        c.delegate = delegate
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
    }
}
