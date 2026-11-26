package grails.plugins

/** Compatibilidade: beans que recebem o {@code pluginManager} (o Grox injeta pelo nome da propriedade). */
interface PluginManagerAware {
    void setPluginManager(GrailsPluginManager pluginManager)
}
