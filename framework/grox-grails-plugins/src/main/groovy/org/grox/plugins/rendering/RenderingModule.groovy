package org.grox.plugins.rendering

import org.grox.core.GroxModule

/** Plugin rendering: {@code pdfRenderingService}, com o flying-saucer-pdf no classpath da aplicação. */
class RenderingModule extends GroxModule {

    private List<Object> services = []

    int getOrder() { 630 }

    @Override
    void contributeBeans(Map<String, Object> beans) {
        if (!runtime.classPresent('org.xhtmlrenderer.pdf.ITextRenderer')) return
        beans.xhtmlDocumentService = new grails.plugins.rendering.document.XhtmlDocumentService()
        beans.pdfRenderingService = new grails.plugins.rendering.pdf.PdfRenderingService()
        services = [beans.xhtmlDocumentService, beans.pdfRenderingService]
    }

    /** Dependem de outros beans. */
    @Override
    void beansReady(Map<String, Object> beans) {
        services.each { runtime.autowirer.autowire(it) }
    }
}
