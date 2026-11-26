package sample

import org.grox.GroxApp
import org.grox.core.GroxRuntime
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test


/** Assets pré-compilados no build (assetCompile): digest, manifest, cache e tags asset:. */
class AssetsTest {

    static GroxRuntime app
    static String base

    @BeforeAll
    static void start() {
        System.setProperty('grails.env', 'test')
        app = GroxApp.run(Application)
        base = "http://localhost:${app.port}"
    }

    @AfterAll
    static void stop() {
        app?.close()
    }

    @Test
    void 'tags apontam para nomes com digest e o bundle inclui os requires'() {
        assert !app.webContext.assets.development
        String html = new TestClient(base).page('/').body()
        String css = (html =~ /href="(\/assets\/application-[0-9a-f]{32}\.css)"/)[0][1]
        String js = (html =~ /<script src="(\/assets\/application-[0-9a-f]{32}\.js)" defer><\/script>/)[0][1]
        assert html =~ /<link href="\/assets\/logo-[0-9a-f]{32}\.svg" rel="icon" type="image\/svg\+xml"\/>/

        def c = new TestClient(base)
        def cssResp = c.send('GET', css)
        assert cssResp.status() == 200 && cssResp.headers().firstValue('Content-Type').get().startsWith('text/css')
        assert cssResp.headers().firstValue('Cache-Control').get() == 'public, max-age=31536000, immutable'
        // base.css veio antes do próprio arquivo (require), e url() aponta para a imagem com digest
        assert cssResp.body().indexOf('font-family: system-ui') < cssResp.body().indexOf('header nav a.atual')
        assert cssResp.body() =~ /url\('\/assets\/logo-[0-9a-f]{32}\.svg'\)/
        def jsBody = c.send('GET', js).body()
        assert jsBody.indexOf('window.grox') < jsBody.indexOf("addEventListener('DOMContentLoaded'")
        assert !jsBody.contains('//= require')

        String etag = cssResp.headers().firstValue('ETag').get()
        assert c.send('GET', css, null, ['If-None-Match': etag]).status() == 304
        // nome lógico também funciona (cache curto) e inexistente dá 404
        assert c.send('GET', '/assets/application.css').headers().firstValue('Cache-Control').get() == 'public, max-age=300'
        assert c.send('GET', '/assets/nao-existe.js').status() == 404
        assert c.send('GET', '/assets/../application.yml').status() in [400, 404]
    }
}
