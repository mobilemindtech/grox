package org.grox

import grails.plugins.rendering.pdf.PdfRenderingService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import java.awt.image.BufferedImage

/** PDF (Flying Saucer): o template só busca recursos do próprio servidor, de data: e dos prefixos permitidos. */
class PdfRenderingTest {

    @TempDir
    File dir

    @Test
    void 'so a origem do base, data e os prefixos permitidos'() {
        def agent = new PdfRenderingService.RestrictedUserAgent(null, 20, 'http://app.local:8080/x', ['https://cdn.exemplo.com/'])
        assert agent.permitted('http://app.local:8080/assets/logo.png') && agent.permitted('data:image/png;base64,AAAA')
        assert agent.permitted('https://cdn.exemplo.com/fonte.ttf')
        // antes: o servidor buscava qualquer URL do template (SSRF) e lia arquivos locais
        ['file:///etc/passwd', 'http://169.254.169.254/latest/meta-data/', 'http://app.local:9090/', 'https://app.local:8080/',
         'http://localhost:8080/actuator', 'jar:file:/x.jar!/a', 'ftp://app.local/x', 'https://cdn.exemplo.com.evil/x'].each {
            assert !agent.permitted(it)
        }
        assert PdfRenderingService.RestrictedUserAgent.originOf('HTTP://App.Local/x') == 'http://app.local:80'
        assert !new PdfRenderingService.RestrictedUserAgent(null, 20, null, []).permitted('http://qualquer/')
    }

    @Test
    void 'imagem de arquivo local nao entra no PDF, a de data entra'() {
        def png = new File(dir, 'segredo.png')
        def img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB)
        (0..<200).each { x -> (0..<200).each { y -> img.setRGB(x, y, (x * 31 + y * 17) * 2654435761 as int) } }
        ImageIO.write(img, 'png', png)
        String dataUri = 'data:image/png;base64,' + png.bytes.encodeBase64()
        Closure<Integer> pdf = { String src ->
            String xhtml = "<html><body><p>Relatório</p>${src ? "<img src=\"${src}\"/>" : ''}</body></html>"
            def doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xhtml.getBytes('UTF-8')))
            def out = new ByteArrayOutputStream()
            new PdfRenderingService().doRender([base: 'http://app.local:8080/'], doc, out)
            out.size()
        }
        int semImagem = pdf(null)
        assert pdf(dataUri) > semImagem + 1000                       // data: continua funcionando
        assert pdf(png.toURI().toString()) < semImagem + 200         // file: recusado (antes, embutido no PDF)
    }
}
