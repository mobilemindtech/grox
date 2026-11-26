package grails.plugins.rendering.pdf

import grails.plugins.rendering.RenderingService
import org.w3c.dom.Document

/** Bean {@code pdfRenderingService}: PDF de um template GSP em XHTML, com o Flying Saucer (OpenPDF). */
class PdfRenderingService extends RenderingService {

    @Override
    protected void doRender(Map args, Document document, OutputStream outputStream) {
        def renderer = new org.xhtmlrenderer.pdf.ITextRenderer()
        renderer.setDocument(document, (String) args.base)
        renderer.layout()
        renderer.createPDF(outputStream)
    }

    @Override
    protected String getDefaultContentType() { 'application/pdf' }
}
