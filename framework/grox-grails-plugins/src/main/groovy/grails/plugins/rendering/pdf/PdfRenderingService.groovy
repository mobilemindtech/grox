package grails.plugins.rendering.pdf

import grails.plugins.rendering.RenderingService
import org.w3c.dom.Document
import org.xhtmlrenderer.pdf.ITextOutputDevice
import org.xhtmlrenderer.pdf.ITextRenderer
import org.xhtmlrenderer.pdf.ITextUserAgent

/**
 * Bean {@code pdfRenderingService}: PDF de um template GSP em XHTML, com o Flying Saucer (OpenPDF).
 * <p>Os recursos que o template referencia (imagens, CSS, fontes) só são buscados na origem do {@code base}
 * (padrão: {@code grails.serverURL}), em {@code data:} e nos prefixos de {@code grox.rendering.allowedUrls}.
 * Sem isso, um template com uma URL vinda do usuário ({@code <img src="${foto}">}) faria o servidor buscar
 * endereços internos ou arquivos locais ({@code file:}).
 */
class PdfRenderingService extends RenderingService {

    @Override
    protected void doRender(Map args, Document document, OutputStream outputStream) {
        def device = new ITextOutputDevice(ITextRenderer.DEFAULT_DOTS_PER_POINT)
        def agent = new RestrictedUserAgent(device, ITextRenderer.DEFAULT_DOTS_PER_PIXEL, (String) args.base, allowedUrls())
        def renderer = new ITextRenderer(ITextRenderer.DEFAULT_DOTS_PER_POINT, ITextRenderer.DEFAULT_DOTS_PER_PIXEL, device, agent)
        renderer.setDocument(document, (String) args.base)
        renderer.layout()
        renderer.createPDF(outputStream)
    }

    /** {@code grox.rendering.allowedUrls}: prefixos de URL que os templates também podem buscar. */
    protected List<String> allowedUrls() {
        Object v = grailsApplication?.config?.getProperty('grox.rendering.allowedUrls', Object)
        v instanceof Collection ? ((Collection) v)*.toString() : v ? v.toString().tokenize(',')*.trim() : []
    }

    @Override
    protected String getDefaultContentType() { 'application/pdf' }

    /** User agent que recusa recursos fora da origem do {@code base} e dos prefixos permitidos. */
    static class RestrictedUserAgent extends ITextUserAgent {

        private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PdfRenderingService)

        final String origin
        final List<String> allowed

        RestrictedUserAgent(ITextOutputDevice device, int dotsPerPixel, String base, List<String> allowed) {
            super(device, dotsPerPixel)
            this.origin = originOf(base)
            this.allowed = allowed ?: []
        }

        @Override
        protected InputStream openStream(String uri) throws IOException {
            if (!permitted(uri)) {
                // o Flying Saucer registra e segue sem o recurso (imagem ou CSS ausente)
                LOG.warn("PDF: recurso fora do servidor recusado: ${uri}")
                throw new IOException("recurso fora do servidor: ${uri}")
            }
            super.openStream(uri)
        }

        boolean permitted(String uri) {
            if (uri == null) return false
            if (uri.startsWith('data:')) return true
            if (allowed.any { uri.startsWith(it) }) return true
            origin != null && originOf(uri) == origin
        }

        /** {@code http://host:porta} (porta padrão explícita); null se não for http(s). */
        static String originOf(String url) {
            if (!url) return null
            try {
                URI u = new URI(url)
                String scheme = u.scheme?.toLowerCase()
                if (!(scheme in ['http', 'https']) || !u.host) return null
                int port = u.port > 0 ? u.port : (scheme == 'https' ? 443 : 80)
                "${scheme}://${u.host.toLowerCase()}:${port}"
            } catch (URISyntaxException ignored) {
                null
            }
        }
    }
}
