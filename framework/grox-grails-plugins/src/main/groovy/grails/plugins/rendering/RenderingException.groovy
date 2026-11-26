package grails.plugins.rendering

/** Erro ao renderizar um documento (plugin rendering). */
class RenderingException extends RuntimeException {
    RenderingException(Throwable cause) { super(cause?.message, cause) }
}
