package org.grox

import grails.util.Environment
import org.grox.core.Env
import org.junit.jupiter.api.Test

/** Ambiente atual no core ({@link Env}) e o {@code Environment} do Grails, que delega para ele. */
class EnvTest {

    @Test
    void 'abreviações, padrão e ambiente customizado'() {
        withProperties(['grails.env': null, 'grox.env': 'dev']) {
            assert Env.currentName == 'development' && Env.development && !Env.defaulted
            assert Environment.current == Environment.DEVELOPMENT
        }
        withProperties(['grails.env': 'homologacao', 'grox.env': null]) {
            assert Env.currentName == 'homologacao' && !Env.development
            assert Environment.current == Environment.CUSTOM && Environment.currentName == 'homologacao'
        }
        if (!System.getenv('GRAILS_ENV') && !System.getenv('GROX_ENV')) {
            withProperties(['grails.env': null, 'grox.env': null, 'grox.env.default': 'test']) {
                assert Env.defaulted && Env.currentName == 'test' && Environment.current == Environment.TEST
            }
        }
    }

    private static void withProperties(Map<String, String> props, Closure body) {
        Map<String, String> saved = props.collectEntries { k, v -> [k, System.getProperty(k)] }
        try {
            props.each { k, v -> v == null ? System.clearProperty(k) : System.setProperty(k, v) }
            body()
        } finally {
            saved.each { k, v -> v == null ? System.clearProperty(k) : System.setProperty(k, v) }
        }
    }
}
