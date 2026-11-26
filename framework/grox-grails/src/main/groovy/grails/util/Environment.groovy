package grails.util

import org.grox.core.Env

/**
 * Emulação de {@code grails.util.Environment}; a resolução do ambiente atual é do core ({@link Env}).
 */
enum Environment {
    DEVELOPMENT('development'),
    TEST('test'),
    PRODUCTION('production'),
    CUSTOM('custom')

    final String name

    Environment(String name) {
        this.name = name
    }

    static String getCurrentName() { Env.currentName }

    /** Ambiente quando nenhum foi definido explicitamente. */
    static String getDefaultName() { Env.defaultName }

    /** O ambiente veio do padrão (nenhuma variável ou propriedade explícita). */
    static boolean isDefaulted() { Env.defaulted }

    static Environment getCurrent() {
        forName(currentName)
    }

    /** O valor do enum para o nome ({@code CUSTOM} se não for um dos padrões). */
    static Environment forName(String n) {
        values().find { it != CUSTOM && it.name == n } ?: CUSTOM
    }

    static boolean isDevelopmentMode() {
        current == DEVELOPMENT
    }

    /**
     * Executa o bloco do ambiente atual:
     * <pre>
     * Environment.executeForCurrentEnvironment {
     *     development { ... }
     *     production { ... }
     * }
     * </pre>
     */
    static Object executeForCurrentEnvironment(Closure<?> blocks) {
        Map<String, Closure> collected = [:]
        def collector = new Object() {
            def methodMissing(String name, Object args) {
                Object[] a = (Object[]) args
                if (a.length == 1 && a[0] instanceof Closure) {
                    collected[name] = (Closure) a[0]
                    return null
                }
                throw new MissingMethodException(name, Environment, a)
            }
        }
        Closure c = (Closure) blocks.clone()
        c.delegate = collector
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        collected[currentName]?.call()
    }
}
