package org.grox.core

import groovy.transform.CompileStatic

/**
 * Ambiente atual, definido por {@code -Dgrails.env}, {@code -Dgrox.env}, {@code GRAILS_ENV} ou
 * {@code GROX_ENV}. Sem nenhum deles vale {@code -Dgrox.env.default} e, por fim, {@code production}:
 * o artefato empacotado é production e o {@code run} do plugin (e os testes) é development.
 * {@code dev} e {@code prod} são abreviações. O {@code Environment} do Grails delega para cá.
 */
@CompileStatic
final class Env {

    static final String DEVELOPMENT = 'development'
    static final String TEST = 'test'
    static final String PRODUCTION = 'production'

    private Env() {}

    static String getCurrentName() {
        String raw = explicit() ?: defaultName
        switch (raw) {
            case 'dev': return DEVELOPMENT
            case 'prod': return PRODUCTION
            default: return raw
        }
    }

    /** Ambiente quando nenhum foi definido explicitamente. */
    static String getDefaultName() {
        System.getProperty('grox.env.default') ?: PRODUCTION
    }

    /** O ambiente veio do padrão (nenhuma variável ou propriedade explícita). */
    static boolean isDefaulted() {
        !explicit()
    }

    static boolean isDevelopment() {
        currentName == DEVELOPMENT
    }

    private static String explicit() {
        System.getProperty('grails.env') ?: System.getProperty('grox.env') ?: System.getenv('GRAILS_ENV') ?: System.getenv('GROX_ENV')
    }
}
