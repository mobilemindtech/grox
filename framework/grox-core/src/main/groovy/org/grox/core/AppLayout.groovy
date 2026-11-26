package org.grox.core

import groovy.transform.CompileStatic

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Nome do diretório do layout Grails: {@code grails-app} (padrão), {@code grox-app} (detectado se existir) ou o
 * definido em {@code grox.appDir} ({@code -Dgrox.appDir}, que o plugin Gradle passa à compilação, ao {@code run}
 * e aos testes a partir de {@code grox { appDir = '...' }}). A AST reconhece os fontes de qualquer um deles.
 */
@CompileStatic
final class AppLayout {

    static final String GRAILS = 'grails-app'
    static final String GROX = 'grox-app'
    static final String PROPERTY = 'grox.appDir'

    private static volatile Pattern pattern
    private static volatile String patternFor

    private AppLayout() {}

    /** O diretório configurado, ou {@code grox-app} se existir no diretório atual, ou {@code grails-app}. */
    static String current(Config config = null) {
        String configured = config != null ? config.getProperty(PROPERTY, String) : System.getProperty(PROPERTY)
        if (configured) return configured
        new File(GROX).directory ? GROX : GRAILS
    }

    /** Subdiretório do layout ({@code controllers}, {@code domain}...) de um arquivo fonte, ou null. */
    static String artefactDir(String path) {
        if (!path) return null
        Matcher m = sourcePattern().matcher(path.replace('\\', '/'))
        m.find() ? m.group(1) : null
    }

    private static Pattern sourcePattern() {
        String custom = System.getProperty(PROPERTY) ?: ''
        if (pattern == null || patternFor != custom) {
            List<String> names = [GRAILS, GROX]
            if (custom && !(custom in names)) names << custom
            pattern = Pattern.compile('/(?:' + names.collect { String n -> Pattern.quote(n) }.join('|') + ')/([^/]+)/')
            patternFor = custom
        }
        pattern
    }
}
