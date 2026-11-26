package grails.web.servlet.mvc

/**
 * Compatibilidade com o {@code GrailsParameterMap}: tipo dos {@code params} (o {@code GroxParams} o
 * estende), para assinaturas como {@code def salvar(GrailsParameterMap params)} em services.
 */
class GrailsParameterMap extends LinkedHashMap<String, Object> {
}
