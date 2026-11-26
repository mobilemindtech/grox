package grails.plugins

import groovy.transform.CompileStatic

/** Compatibilidade: um plugin carregado ({@code pluginManager.allPlugins}), com nome e versão. */
@CompileStatic
class GrailsPlugin {
    final String name
    final String version
    /** O descritor {@code *GrailsPlugin} (null para os plugins embutidos no Grox). */
    final Plugin instance

    GrailsPlugin(String name, String version, Plugin instance) {
        this.name = name
        this.version = version
        this.instance = instance
    }

    @Override
    String toString() { "${name}:${version}" }
}
