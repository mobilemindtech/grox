package org.grox.dsl

/**
 * Delegate dos blocos {@code config { }} e {@code security { }} da DSL de projeto: atribuições na mesma árvore
 * do {@code application.yml} ({@code server.port = 8080}, {@code grox.routes.execution = 'actor'}), com blocos
 * aninhados ({@code grox { routes { poolSize = 4 } }}).
 */
class ConfigBlock extends GroovyObjectSupport {

    final Map<String, Object> target

    ConfigBlock(Map<String, Object> target) {
        this.target = target
    }

    @Override
    Object getProperty(String name) {
        Object v = target[name]
        if (v == null) {
            v = new LinkedHashMap<String, Object>()
            target[name] = v
        }
        v instanceof Map ? new ConfigBlock((Map<String, Object>) v) : v
    }

    @Override
    void setProperty(String name, Object value) {
        target[name] = value
    }

    /** {@code grox { routes { execution = 'actor' } }}. */
    Object invokeMethod(String name, Object args) {
        Object[] a = (Object[]) args
        if (a.length == 1 && a[0] instanceof Closure) {
            ConfigBlock nested = (ConfigBlock) getProperty(name)
            Closure c = (Closure) ((Closure) a[0]).clone()
            c.delegate = nested
            c.resolveStrategy = Closure.DELEGATE_FIRST
            c.call()
            return nested
        }
        throw new MissingMethodException(name, ConfigBlock, a)
    }
}
