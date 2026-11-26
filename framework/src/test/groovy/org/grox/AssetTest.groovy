package org.grox

import org.grox.assets.AssetCompiler
import org.grox.assets.AssetException
import org.grox.assets.AssetProcessor
import org.grox.assets.AssetServer
import org.grox.http.GroxRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.assertThrows

class AssetTest {

    @TempDir
    File dir
    File assets

    void write(String path, String text) {
        def f = new File(assets, path)
        f.parentFile.mkdirs()
        f.text = text
    }

    @BeforeEach
    void setup() {
        assets = new File(dir, 'assets')
        write('javascripts/application.js', '//= require lib/a\n//= require_tree modulos\n//= require_self\nconsole.log("app")\n')
        write('javascripts/lib/a.js', '//= require b\nvar a = 1\n')
        write('javascripts/lib/b.js', 'var b = 2\n')
        write('javascripts/modulos/x.js', '//= require ../lib/b\nvar x = 3\n')
        write('javascripts/modulos/y.js', 'var y = 4\n')
        write('stylesheets/application.css', '/*\n *= require base\n */\n.logo { background: url(../images/logo.png) } .ext { background: url(https://x/y.png) }\n')
        write('stylesheets/base.css', 'body { margin: 0 }\n')
        write('images/logo.png', 'PNG')
    }

    static String text(AssetProcessor p, String logical, Closure<String> url = { '/assets/' + it }) {
        new String(p.process(logical, url).content, 'UTF-8')
    }

    @Test
    void 'require, require_tree, require_self, ordem e sem duplicar'() {
        def p = new AssetProcessor(assets)
        assert p.roots*.name == ['javascripts', 'stylesheets', 'images']
        String js = text(p, 'application.js')
        assert js.readLines().findAll { it.startsWith('var') || it.startsWith('console') } == ['var b = 2', 'var a = 1', 'var x = 3', 'var y = 4', 'console.log("app")']
        assert !js.contains('//= require')
        assert p.process('nada.js') { it } == null
        assert p.allLogicalPaths() == ['application.css', 'application.js', 'base.css', 'lib/a.js', 'lib/b.js', 'logo.png', 'modulos/x.js', 'modulos/y.js']
    }

    @Test
    void 'css com require e url reescrita'() {
        String css = text(new AssetProcessor(assets), 'application.css')
        assert css.startsWith('body { margin: 0 }')
        assert css.contains("url('/assets/logo.png')") && css.contains('url(https://x/y.png)')
    }

    @Test
    void 'require inexistente e caminhos invalidos'() {
        // como o asset-pipeline ("Unable to Locate Asset"): só avisa e segue com o resto
        write('javascripts/ruim.js', '//= require naoexiste\n//= require_self\nvar ok = 1;\n')
        String js = text(new AssetProcessor(assets), 'ruim.js')
        assert js.contains('var ok = 1;') && !js.contains('naoexiste')
        assert new AssetProcessor(assets).find('../assets/javascripts/application.js') == null
    }

    @Test
    void 'compilador gera digest e manifest, servidor de desenvolvimento recarrega'() {
        File out = new File(dir, 'out')
        assert AssetCompiler.compile(assets, out) == 8
        def props = new Properties()
        new File(out, AssetCompiler.MANIFEST).withReader { props.load(it) }
        assert props.getProperty('application.js') ==~ /application-[0-9a-f]{32}\.js/
        String css = new File(out, "${AssetCompiler.OUTPUT}/${props.getProperty('application.css')}").text
        assert css.contains("url('/assets/${props.getProperty('logo.png')}')")

        def server = new AssetServer('assets', assets, AssetTest.classLoader)
        assert server.development && server.url('application.js') == '/assets/application.js'
        def req = new GroxRequest(method: 'GET', path: '/assets/lib/b.js', uri: '/assets/lib/b.js')
        assert server.serve(req).text == 'var b = 2\n'
        Thread.sleep(20)
        write('javascripts/lib/b.js', 'var b = 20\n')
        new File(assets, 'javascripts/lib/b.js').lastModified = System.currentTimeMillis() + 2000
        assert server.serve(req).text == 'var b = 20\n'
        assert server.serve(new GroxRequest(method: 'GET', path: '/assets/application.js', uri: '/x')).text.contains('var b = 20')
    }
}
