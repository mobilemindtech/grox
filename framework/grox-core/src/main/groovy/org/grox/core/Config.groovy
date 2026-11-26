package org.grox.core

import groovy.yaml.YamlSlurper

/**
 * Configuração carregada de {@code application.yml} (classpath), com suporte a
 * múltiplos documentos YAML ({@code ---}), bloco {@code environments} e chaves
 * com ponto ({@code grails.profile: web}), e de {@code application.groovy} e
 * {@code runtime.groovy} (sintaxe do {@code ConfigSlurper}, com {@code environments { }}),
 * mesclados como no Grails: numa chave repetida, o {@code application.groovy} vence o YAML
 * e o {@code runtime.groovy} vence o {@code application.groovy}.
 *
 * Precedência: propriedade de sistema > variável de ambiente
 * ({@code server.port} → {@code SERVER_PORT}) > {@code runtime.groovy} > {@code application.groovy} > YAML. A sobreposição vale só para
 * valores-folha: um bloco (Map) do YAML nunca é trocado por uma variável de ambiente
 * homônima (ex.: {@code MAIL} do shell não esconde {@code mail:}).
 *
 * Arquivos listados em {@code spring.config.import} (ex.: {@code classpath:shared.yml,classpath:rabbit.groovy}) são
 * mesclados por cima do arquivo que os importou, como no Spring Boot.
 *
 * Valores com placeholders {@code ${NOME}} / {@code ${NOME:padrão}} são resolvidos na
 * carga: propriedade de sistema, variável de ambiente ou outra chave da configuração.
 */
class Config {

    private final Map<String, Object> root

    Config(Map<String, Object> root) {
        this.root = root
    }

    /** Configuração em Groovy, na ordem em que é mesclada (a seguinte vence a anterior). */
    static final List<String> GROOVY_FILES = ['application.groovy', 'runtime.groovy']

    static Config load(ClassLoader loader, String envName, List<String> files = ['application.yml', 'application.yaml'],
                       List<String> groovyFiles = GROOVY_FILES) {
        Map<String, Object> merged = [:]
        List<String> imports = []
        for (String name : files) {
            String text = loader.getResource(name)?.getText('UTF-8')
            if (text) {
                parseYaml(text).each { Map doc ->
                    imports.addAll(importsOf(doc, envName))
                    deepMerge(merged, doc)
                }
            }
        }
        Object envs = merged.remove('environments')
        if (envs instanceof Map && ((Map) envs)[envName] instanceof Map) {
            deepMerge(merged, (Map) ((Map) envs)[envName])
        }
        Set<String> seen = new HashSet<>(files + groovyFiles)
        mergeImports(merged, imports, envName, loader, seen)
        for (String name : groovyFiles) {
            String text = loader.getResource(name)?.getText('UTF-8')
            if (text) {
                Map groovy = parseGroovy(text, envName, loader)
                deepMerge(merged, groovy)
                mergeImports(merged, importsOf(groovy, envName), envName, loader, seen)
            }
        }
        resolvePlaceholders(merged, merged)
        new Config(merged)
    }

    /**
     * {@code spring.config.import} do Spring Boot (que o Grails 8 usa para compartilhar configuração entre apps):
     * string separada por vírgula ou lista, com os prefixos {@code classpath:} (o padrão), {@code file:} e
     * {@code optional:}. Como no Spring Boot, o arquivo importado vence o que o importou; o {@code .yml} tem o seu
     * bloco {@code environments} aplicado, o {@code .groovy} é lido pelo {@code ConfigSlurper}, e os importados
     * podem importar outros.
     */
    static List<String> importsOf(Map source, String envName) {
        List<String> result = []
        [source, (source.environments instanceof Map ? ((Map) source.environments)[envName] : null)].each { Object node ->
            Object value = node instanceof Map ? lookup((Map) node, 'spring.config.import') : null
            List items = value instanceof List ? (List) value : (value instanceof String ? ((String) value).split(',') as List : [])
            items.each { if (it?.toString()?.trim()) result << it.toString().trim() }
        }
        result
    }

    private static void mergeImports(Map merged, List<String> imports, String envName, ClassLoader loader, Set<String> seen) {
        for (String spec : imports) {
            String location = spec
            boolean optional = location.startsWith('optional:')
            if (optional) location = location.substring('optional:'.length())
            if (!seen.add(location)) continue
            String text
            if (location.startsWith('file:')) {
                File file = new File(location.substring('file:'.length()))
                text = file.isFile() ? file.getText('UTF-8') : null
            } else {
                String name = location.startsWith('classpath:') ? location.substring('classpath:'.length()) : location
                text = loader.getResource(name.startsWith('/') ? name.substring(1) : name)?.getText('UTF-8')
            }
            if (text == null) {
                if (optional) continue
                throw new IllegalStateException("spring.config.import: ${spec} não encontrado (use optional: se puder faltar)")
            }
            Map source
            if (location.endsWith('.groovy')) {
                source = parseGroovy(text, envName, loader)
            } else {
                source = [:]
                parseYaml(text).each { deepMerge(source, it) }
            }
            List<String> nested = importsOf(source, envName)
            Object envs = source.remove('environments')
            deepMerge(merged, source)
            if (envs instanceof Map && ((Map) envs)[envName] instanceof Map) deepMerge(merged, (Map) ((Map) envs)[envName])
            mergeImports(merged, nested, envName, loader, seen)
        }
    }

    private static final java.util.regex.Pattern PLACEHOLDER = ~/\$\{([^}:]+)(?::([^}]*))?}/

    static void resolvePlaceholders(Map node, Map root) {
        node.each { k, v ->
            if (v instanceof Map) resolvePlaceholders((Map) v, root)
            else if (v instanceof List) node[k] = resolveList((List) v, root)
            else if (v instanceof String && ((String) v).contains('${')) node[k] = resolve((String) v, root)
        }
    }

    /** Listas de valores e de mapas ({@code rabbitmq.connections: [- host: "${HOST:}"]}). */
    private static List resolveList(List list, Map root) {
        list.collect { item ->
            if (item instanceof Map) {
                resolvePlaceholders((Map) item, root)
                return item
            }
            if (item instanceof List) return resolveList((List) item, root)
            item instanceof String && ((String) item).contains('${') ? resolve((String) item, root) : item
        }
    }

    static String resolve(String value, Map root) {
        value.replaceAll(PLACEHOLDER) { List<String> m ->
            String name = m[1].trim()
            Object v = System.getProperty(name) ?: System.getenv(name)
            if (v == null) v = lookup(root, name)
            if (v == null || v instanceof Map) v = m[2]
            v == null ? '' : v.toString()
        }
    }

    private static Object lookup(Map root, String path) {
        Object current = root
        for (String part : path.split(/\./)) {
            if (!(current instanceof Map)) return null
            current = ((Map) current).get(part)
        }
        current
    }

    /**
     * Um arquivo no formato do {@code ConfigSlurper} ({@code grails.x.y = 1}, blocos aninhados), já com o bloco
     * {@code environments { }} do ambiente aplicado. Classes da aplicação podem ser referenciadas (como no
     * {@code runtime.groovy} do Grails).
     */
    static Map parseGroovy(String text, String envName, ClassLoader loader) {
        def slurper = new ConfigSlurper(envName)
        slurper.classLoader = new GroovyClassLoader(loader)
        (Map) plain(slurper.parse(text))
    }

    /** ConfigObject → mapas comuns; os blocos vazios que o script só leu (sem atribuir) saem. */
    private static Object plain(Object value) {
        if (!(value instanceof Map)) return value
        Map result = [:]
        ((Map) value).each { k, v ->
            if (v instanceof ConfigObject && ((ConfigObject) v).isEmpty()) return
            result[k.toString()] = plain(v)
        }
        result
    }

    static List<Map> parseYaml(String text) {
        def slurper = new YamlSlurper()
        List<Map> docs = []
        for (String doc : text.split(/(?m)^---\s*$/)) {
            if (!doc.trim()) continue
            Object parsed = slurper.parseText(doc)
            if (parsed instanceof Map) docs << normalize((Map) parsed)
        }
        docs
    }

    /** Expande chaves com ponto em mapas aninhados. */
    static Map normalize(Map source) {
        Map result = [:]
        source.each { k, v ->
            Object value = v instanceof Map ? normalize((Map) v) : v
            List<String> parts = k.toString().tokenize('.')
            Map target = result
            for (String p : parts.init()) {
                if (!(target[p] instanceof Map)) target[p] = [:]
                target = (Map) target[p]
            }
            String last = parts.last()
            if (value instanceof Map && target[last] instanceof Map) deepMerge((Map) target[last], (Map) value)
            else target[last] = value
        }
        result
    }

    static void deepMerge(Map target, Map source) {
        source.each { k, v ->
            if (v instanceof Map && target[k] instanceof Map) deepMerge((Map) target[k], (Map) v)
            else if (v instanceof Map) {
                Map copy = [:]
                deepMerge(copy, (Map) v)
                target[k] = copy
            } else target[k] = v
        }
    }

    /**
     * Valores padrão vindos do código (o {@code config { }} da DSL de projeto): só entram as chaves que os
     * arquivos não definem; propriedades de sistema e variáveis de ambiente continuam por cima.
     */
    void mergeDefaults(Map defaults) {
        Map copy = [:]
        deepMerge(copy, defaults)
        resolvePlaceholders(copy, root)
        fill(root, copy)
    }

    private static void fill(Map target, Map defaults) {
        defaults.each { k, v ->
            if (!target.containsKey(k)) target[k] = v
            else if (v instanceof Map && target[k] instanceof Map) fill((Map) target[k], (Map) v)
        }
    }

    Object navigate(String path) {
        Object yaml = lookup(root, path)
        if (yaml instanceof Map) return yaml
        String sys = System.getProperty(path)
        if (sys != null) return sys
        String env = System.getenv(path.toUpperCase().replaceAll(/[.\-]/, '_'))
        if (env != null) return env
        yaml
    }

    Object get(String path) {
        navigate(path)
    }

    Object getAt(String path) {
        navigate(path)
    }

    public <T> T getProperty(String path, Class<T> type) {
        getProperty(path, type, null)
    }

    public <T> T getProperty(String path, Class<T> type, T defaultValue) {
        Object v = navigate(path)
        v == null ? defaultValue : convert(v, type)
    }

    /**
     * {@code config.getProperty('a.b')} (API do Grails) e navegação estilo
     * {@code config.server.port} / {@code config.mail.listener}.
     */
    Object getProperty(String path) {
        Object v = navigate(path)
        // config.chaveInexistente.outra: navegação null-safe como no Grails (caminho pontuado explícito: null)
        if (v == null) return path.contains('.') ? null : new NullSafeNavigator([path])
        ConfigView.wrap(v)
    }

    Map<String, Object> toMap() {
        root
    }

    static <T> T convert(Object v, Class<T> type) {
        if (type.isInstance(v)) return (T) v
        if (type == Boolean || type == boolean) return (T) Boolean.valueOf(v.toString().trim())
        if (type == String) return (T) v.toString()
        (T) v.toString().trim().asType(type)
    }
}
