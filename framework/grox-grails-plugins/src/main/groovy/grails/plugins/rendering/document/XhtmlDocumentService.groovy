package grails.plugins.rendering.document

import grails.gsp.PageRenderer
import org.w3c.dom.Document
import org.xml.sax.InputSource

/**
 * Bean {@code xhtmlDocumentService} do plugin rendering: renderiza o template GSP ({@code template:},
 * {@code model:}, {@code controller:}) e o carrega como documento XHTML do Flying Saucer.
 */
class XhtmlDocumentService {

    PageRenderer groovyPageRenderer

    Document createDocument(Map args) {
        createDocument(generateXhtml(args))
    }

    Document createDocument(String xhtml) {
        try {
            org.xhtmlrenderer.resource.XMLResource.load(new InputSource(new StringReader(xhtml))).document
        } catch (Exception e) {
            throw new IllegalArgumentException("XHTML inválido para o PDF: ${e.message}", e)
        }
    }

    String generateXhtml(Map args) {
        if (!args.template) throw new IllegalArgumentException("O argumento 'template' é obrigatório")
        groovyPageRenderer.render(template: templatePath(args), model: args.model ?: [:])
    }

    /** Relativo ao controller ({@code controller: this} ou o nome), como no plugin. */
    static String templatePath(Map args) {
        String t = args.template.toString()
        if (t.startsWith('/') || !args.controller) return t
        String controller = args.controller instanceof CharSequence ? args.controller.toString()
                : org.grox.core.ClassUtils.userClass(args.controller.getClass()).simpleName.replaceFirst(/Controller$/, '').uncapitalize()
        "/${controller}/${t}"
    }
}
