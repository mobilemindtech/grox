package grails.plugins.rendering

import grails.plugins.rendering.document.XhtmlDocumentService
import org.w3c.dom.Document

/**
 * Base dos services do plugin rendering ({@code pdfRenderingService}): {@code render(args)} devolve um
 * {@code ByteArrayOutputStream}, {@code render(args, outputStream)} escreve no stream e
 * {@code render(args, response)} escreve na resposta ({@code contentType}, {@code filename} vira
 * Content-Disposition attachment). {@code base} (ou {@code grails.serverURL}) resolve URLs relativas.
 */
abstract class RenderingService {

    XhtmlDocumentService xhtmlDocumentService
    def grailsApplication

    protected abstract void doRender(Map args, Document document, OutputStream outputStream)

    protected abstract String getDefaultContentType()

    OutputStream render(Map args, OutputStream outputStream = new ByteArrayOutputStream()) {
        try {
            processArgs(args)
            Document document = (Document) (args.document ?: xhtmlDocumentService.createDocument(args))
            doRender(args, document, outputStream)
            outputStream
        } catch (RenderingException e) {
            throw e
        } catch (Exception e) {
            throw new RenderingException(e)
        }
    }

    /** Resposta HTTP do Grox (ou a {@code response} de um controller). Devolve false, como no plugin. */
    boolean render(Map args, org.grox.http.GroxResponse response) {
        processArgs(args)
        byte[] bytes = args.bytes ? (byte[]) args.bytes
                : args.input ? ((InputStream) args.input).bytes
                : ((ByteArrayOutputStream) render(args)).toByteArray()
        response.contentType = (args.contentType ?: defaultContentType).toString()
        if (args.filename) response.setHeader('Content-Disposition', org.grox.web.ControllerSupport.contentDisposition(args.filename.toString()))
        response.outputStream.write(bytes)
        false
    }

    protected void processArgs(Map args) {
        if (!args.base) args.base = grailsApplication?.config?.getProperty('grails.serverURL', String)
    }
}
