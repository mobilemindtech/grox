package grails.plugins

import groovy.transform.CompileStatic

/**
 * Compatibilidade com o bean {@code pluginManager} do Grails: os descritores {@code *GrailsPlugin} das
 * bibliotecas ({@code allPlugins}, {@code hasGrailsPlugin}, {@code getGrailsPlugin}).
 */
@CompileStatic
class GrailsPluginManager {
    final List<GrailsPlugin> allPlugins

    GrailsPluginManager(List<Plugin> descriptors) {
        allPlugins = descriptors.collect { Plugin p ->
            String simple = p.getClass().simpleName
            String base = simple.endsWith('GrailsPlugin') ? simple.substring(0, simple.length() - 'GrailsPlugin'.length()) : simple
            // RedisGrailsPlugin → redis; GrailsSwaggerGrailsPlugin → grailsSwagger (nome lógico do Grails)
            String name = base ? base.substring(0, 1).toLowerCase() + base.substring(1) : simple
            new GrailsPlugin(name, p.getClass().package?.implementationVersion ?: '0.0', p)
        }.asImmutable()
    }

    GrailsPlugin[] getAllPlugins() { allPlugins as GrailsPlugin[] }

    boolean hasGrailsPlugin(String name) { getGrailsPlugin(name) != null }

    GrailsPlugin getGrailsPlugin(String name) { allPlugins.find { it.name == name } }
}
