package org.grox

import org.grox.core.Config
import org.junit.jupiter.api.Test

class ConfigTest {

    @Test
    void 'placeholders dentro de listas de mapas'() {
        Map m = [app: [host: 'h1'], rabbitmq: [connections: [[name: 'a', host: '${app.host}', port: '${NAO_EXISTE_X:5672}'], ['${app.host}', ['${app.host}']]]]]
        org.grox.core.Config.resolvePlaceholders(m, m)
        assert m.rabbitmq.connections[0] == [name: 'a', host: 'h1', port: '5672']
        assert m.rabbitmq.connections[1] == ['h1', ['h1']]
    }


    @Test
    void 'chave inexistente navegada com ponto e null-safe como no Grails'() {
        def config = new org.grox.core.Config([rabbitmq: [ampq: [publishers: [[exchange: 'app']]]]])
        int n = 0
        for (def e in config.ampq.publishers) n++          // config.ampq não existe (está em rabbitmq.ampq)
        assert n == 0 && !config.ampq && (config.ampq.x.y ?: 'padrao') == 'padrao'
        assert config.rabbitmq.ampq.publishers*.exchange == ['app']
        assert !config.rabbitmq.build && (config.rabbitmq.build.at ?: 'p') == 'p'   // null-safe em qualquer nível
        assert config.getProperty('nao.existe') == null       // caminho pontuado explícito: null
        assert config.getProperty('nao.existe', String, 'p') == 'p'
    }


    static final String YAML = '''
server:
  port: 8080
grails.profile: web
grox:
  controllers:
    poolSize: 4
---
environments:
  test:
    server:
      port: 0
    grox.controllers.concurrency: request
'''

    static Config load(String env, Map<String, String> files = ['application.yml': YAML]) {
        def loader = new ClassLoader(ConfigTest.classLoader) {
            @Override
            URL getResource(String name) {
                String text = files[name]
                text == null ? null : new URL(null, 'mem:' + name, new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL u) {
                        new URLConnection(u) {
                            void connect() {}

                            InputStream getInputStream() { new ByteArrayInputStream(text.getBytes('UTF-8')) }
                        }
                    }
                })
            }
        }
        Config.load(loader, env)
    }

    @Test
    void 'multiplos documentos, chaves com ponto e ambientes'() {
        def dev = load('development')
        assert dev.getProperty('server.port', Integer) == 8080
        assert dev.getProperty('grails.profile', String) == 'web'
        assert dev.getProperty('grox.controllers.poolSize', Integer) == 4
        assert dev.getProperty('grox.controllers.concurrency', String, 'pool') == 'pool'
        assert dev.server.port == 8080

        def test = load('test')
        assert test.getProperty('server.port', Integer) == 0
        assert test.getProperty('grox.controllers.concurrency', String) == 'request'
        assert test.getProperty('grox.controllers.poolSize', Integer) == 4
    }

    @Test
    void 'propriedade de sistema tem precedencia'() {
        System.setProperty('grox.teste.valor', 'true')
        try {
            assert load('development').getProperty('grox.teste.valor', Boolean) == true
        } finally {
            System.clearProperty('grox.teste.valor')
        }
    }

    @Test
    void 'ambiente padrao e production, exceto com o padrao do plugin ou explicito'() {
        Map<String, String> saved = ['grails.env', 'grox.env', 'grox.env.default'].collectEntries { [it, System.getProperty(it)] }
        try {
            saved.keySet().each { System.clearProperty(it) }
            if (System.getenv('GRAILS_ENV') || System.getenv('GROX_ENV')) return   // variável explícita no ambiente do build
            assert grails.util.Environment.currentName == 'production' && grails.util.Environment.defaulted
            System.setProperty('grox.env.default', 'development')
            assert grails.util.Environment.currentName == 'development' && grails.util.Environment.defaulted
            System.setProperty('grails.env', 'prod')
            assert grails.util.Environment.currentName == 'production' && !grails.util.Environment.defaulted
        } finally {
            saved.each { k, v -> v == null ? System.clearProperty(k) : System.setProperty(k, v) }
        }
    }

    static final String GROOVY = '''
server.port = 9090
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
    [pattern: '/', access: ['permitAll']],
    [pattern: '/api/**', access: ['ROLE_ADMIN']]
]
grox {
    origem = 'application.groovy'
    padrao = grails.naoExiste ?: 'lido sem criar bloco'
}
environments {
    test {
        grox.extra = 'só no test'
    }
}
'''

    @Test
    void 'application groovy e runtime groovy mesclados ao yml como no Grails'() {
        Map<String, String> files = ['application.yml': YAML, 'application.groovy': GROOVY,
                                     'runtime.groovy'    : "grox.origem = 'runtime.groovy'\ngrox.tipo = org.grox.core.Config.name"]
        def dev = load('development', files)
        assert dev.getProperty('server.port', Integer) == 9090                  // o groovy vence o yml
        assert dev.getProperty('grails.profile', String) == 'web'                // chaves só do yml continuam
        assert dev.getProperty('grox.controllers.poolSize', Integer) == 4
        assert dev.getProperty('grox.origem', String) == 'runtime.groovy'          // o runtime.groovy vence o application.groovy
        assert dev.getProperty('grox.tipo', String) == 'org.grox.core.Config'       // classes no runtime.groovy
        assert dev.getProperty('grox.padrao', String) == 'lido sem criar bloco' && dev.get('grails.naoExiste') == null
        assert dev.grails.plugin.springsecurity.controllerAnnotations.staticRules*.pattern == ['/', '/api/**']
        assert dev.getProperty('grox.extra') == null
        def test = load('test', files)
        assert test.getProperty('grox.extra', String) == 'só no test'
        assert test.getProperty('server.port', Integer) == 9090                 // o groovy vence também o environments do yml
        System.setProperty('grox.origem', 'sistema')
        try {
            assert load('development', files).getProperty('grox.origem', String) == 'sistema'
        } finally {
            System.clearProperty('grox.origem')
        }
    }

    @Test
    void 'spring config import mescla os arquivos importados por cima de quem importou'() {
        Map<String, String> files = [
                'application.yml'  : """
server.port: 8080
app.nome: app-c-web
spring:
  config:
    import: classpath:shared.yml, optional:classpath:nao-existe.yml,classpath:rabbit.groovy
""",
                'shared.yml'       : """
app.nome: compartilhado
dataSource.url: jdbc:h2:mem:x
hibernate.cache: true
environments:
  test:
    dataSource.url: jdbc:h2:mem:teste
spring.config.import: [classpath:aninhado.yml]
""",
                'aninhado.yml'     : 'app.aninhado: sim',
                'rabbit.groovy'    : "rabbitmq.ampq.routes = ['a', 'b']\nenvironments { test { rabbitmq.enabled = false } }",
                'application.groovy': "app.nome = 'groovy'"]
        def dev = load('development', files)
        assert dev.getProperty('server.port', Integer) == 8080
        assert dev.getProperty('dataSource.url', String) == 'jdbc:h2:mem:x'
        assert dev.getProperty('app.aninhado', String) == 'sim'                 // importado por um importado
        assert dev.rabbitmq.ampq.routes == ['a', 'b']
        assert dev.getProperty('app.nome', String) == 'groovy'                  // o application.groovy vence os importados do yml
        def test = load('test', files.findAll { it.key != 'application.groovy' })
        assert test.getProperty('app.nome', String) == 'compartilhado'           // o importado vence o yml que o importou
        assert test.getProperty('dataSource.url', String) == 'jdbc:h2:mem:teste' // environments do importado
        assert test.getProperty('rabbitmq.enabled', Boolean) == false
        def falta = shouldFail(IllegalStateException) {
            load('test', ['application.yml': 'spring.config.import: classpath:nao-existe.yml'])
        }
        assert falta.message.contains('nao-existe.yml')
    }

    private static Throwable shouldFail(Class<? extends Throwable> type, Closure body) {
        try {
            body()
        } catch (Throwable e) {
            assert type.isInstance(e)
            return e
        }
        throw new AssertionError("esperava ${type.simpleName}")
    }
}
