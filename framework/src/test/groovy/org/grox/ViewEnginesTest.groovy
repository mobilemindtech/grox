package org.grox

import de.neuland.pug4j.exceptions.PugException
import org.grox.markdown.Markdown
import org.grox.markdown.MarkdownTagLib
import org.grox.markdown.MarkdownViews
import org.grox.pug.PugViews
import org.grox.view.GspViews
import org.grox.view.gsp.GspRenderContext
import org.grox.view.gsp.GspTemplateEngine
import org.grox.view.gsp.ViewLocator
import org.grox.view.taglib.RenderTagLib
import org.grox.view.taglib.TagLibRegistry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.assertThrows

/** Views em Pug e Markdown ({@code grox-pug}, {@code grox-markdown}) ao lado das GSPs, com as TagLibs. */
class ViewEnginesTest {

    @TempDir
    static File dir

    static GspTemplateEngine engine
    static GspViews views
    static PugViews pug
    static MarkdownViews md

    @BeforeAll
    static void setup() {
        def tags = new TagLibRegistry()
        tags.register(new RenderTagLib())
        tags.register(new GspTest.TesteTagLib())
        tags.register(new MarkdownTagLib())
        def locator = new ViewLocator(dir, ViewEnginesTest.classLoader)
        engine = new GspTemplateEngine(locator, true, tags, ViewEnginesTest.classLoader)
        views = new GspViews(engine, tags, null)
        pug = new PugViews(locator, true, ViewEnginesTest.classLoader, [markdown: new org.grox.pug.MarkdownFilter()])
        md = new MarkdownViews(locator, engine, true)
        views.register(pug)
        views.register(md)
    }

    static void write(String file, String text) {
        def f = new File(dir, file)
        f.parentFile.mkdirs()
        long before = f.exists() ? f.lastModified() : 0
        f.text = text
        f.setLastModified(Math.max(System.currentTimeMillis(), before + 2000))
    }

    static String render(String path, Map model = [:]) {
        def sw = new StringWriter()
        def rc = new GspRenderContext(null, views, sw)
        rc.bind {
            def gsp = engine.find(path)
            if (gsp != null) engine.render(gsp, model, rc)
            else views.engineFor(path).render(path, model, rc)
        }
        sw.toString()
    }

    @Test
    void 'pug: expressoes em Groovy, variavel ausente nula, var, verdade do Groovy e each em mapa'() {
        write('pg/expr.pug', '''\
- var dobro = n * 2
p #{dobro} #{livros*.titulo.join(', ')} #{livros.find { it.ano > 1900 }?.titulo}
p= ausente == null ? 'nulo' : 'definido'
if vazia
  p nao
else
  p lista vazia é falsa
each v, k in mapa
  i #{k}=#{v}
p #{"GString ${n}"} #{inexistente?.x?.y ?: 'padrao'}''')
        String html = render('/pg/expr', [n: 21, vazia: [], mapa: [a: 1, b: 2],
                                          livros: [[titulo: 'A', ano: 1890], [titulo: 'B', ano: 1950]]])
        assert html == '<p>42 A, B B</p><p>nulo</p><p>lista vazia é falsa</p><i>a=1</i><i>b=2</i><p>GString 21 padrao</p>'
    }

    @Test
    void 'pug: TagLibs como elementos, corpo com it e Map, tag como metodo e saida sem escape'() {
        write('pg/tags.pug', '''\
t:caixa(titulo='T' + n)
  b corpo #{n}
t:repetir(vezes=3) [#{it}]
t:comVars
  | #{nome}:#{idade}
p #{t.soma(a: 1, b: n)}
p!= t.upper(texto: 'a<b>')
p= '<x>'
t:upper(texto='<i>')''')
        String html = render('/pg/tags', [n: 2])
        assert html == '<div title="T2"><b>corpo 2</b></div>[1][2][3]Bia:7<p>3</p><p>A<B></p><p>&lt;x&gt;</p><I>'
    }

    @Test
    void 'pug: tag como metodo dentro de #{} e p= nao e escapada de novo, e a expressao roda uma vez'() {
        write('pg/safe.pug', '''\
p #{t.upper(texto: "d'agua & <i>")}
p= t.upper(texto: '<b>')
p #{'<x>'}
p #{contador << 1; contador.size()}''')
        def contador = []
        String html = render('/pg/safe', [contador: contador])
        // a saída da tag vai como está, como no ${} da GSP (antes: escapada de novo pelo pug4j)
        assert html == "<p>D'AGUA & <I></p><p><B></p><p>&lt;x&gt;</p><p>1</p>"
        assert contador.size() == 1
    }

    @Test
    void 'pug: extends, include, mixin e filtro markdown com HTML escapado'() {
        write('layouts/base.pug', 'html\n  body\n    block conteudo\n')
        write('pg/_menu.pug', 'nav menu\n')
        write('pg/filho.pug', '''\
extends /layouts/base
mixin item(x)
  li= x
block conteudo
  include _menu
  ul
    +item('um')
  :markdown
    **forte** <script>x</script>''')
        assert render('/pg/filho') == '<html><body><nav>menu</nav><ul><li>um</li></ul><p><strong>forte</strong> &lt;script&gt;x&lt;/script&gt;</p>\n</body></html>'
    }

    @Test
    void 'pug: recarrega o template alterado e erro de sintaxe com o arquivo'() {
        write('pg/muda.pug', 'p v1')
        assert render('/pg/muda') == '<p>v1</p>'
        write('pg/muda.pug', 'p v2')
        assert render('/pg/muda') == '<p>v2</p>'
        write('pg/erro.pug', 'p #{1 +}')
        def e = assertThrows(PugException) { render('/pg/erro') }
        assert e.message.contains('erro')
    }

    @Test
    void 'markdown: GSP antes do commonmark, front matter vira title e meta, template sem documento'() {
        write('md/pagina.md', '''\
---
title: Página ${n}
layout: main
---
# Título

Valor: **${n}** e ${html} <t:upper texto="x"/>

- [link](javascript:alert(1))
''')
        String html = render('/md/pagina', [n: 3, html: '<b>'])
        assert html.contains('<title>Página 3</title>') && html.contains('<meta name="layout" content="main"/>')
        assert html.contains('<h1 id="título">Título</h1>') || html.contains('<h1 id="titulo">Título</h1>')
        assert html.contains('Valor: <strong>3</strong> e &lt;b&gt; X')
        assert !html.contains('javascript:')
        write('md/_parte.md', '*${n}*')
        assert render('/md/_parte', [n: 5]) == '<p><em>5</em></p>\n'
    }

    @Test
    void 'g render template acha partials em Pug e Markdown, e md render escapa HTML'() {
        write('gs/_card.pug', 'div.card= it')
        write('gs/_nota.md', '**${x}**')
        write('gs/pagina.gsp', '<g:render template="/gs/card" collection="${[1, 2]}"/>|<g:render template="/gs/nota" model="[x: 9]"/>|<md:render text="${texto}"/>|<md:render trusted="true">_ok_ <u>u</u></md:render>')
        String html = render('/gs/pagina', [texto: 'a <script>b</script>'])
        assert html == '<div class="card">1</div><div class="card">2</div>|<p><strong>9</strong></p>\n|<p>a &lt;script&gt;b&lt;/script&gt;</p>\n|<p><em>ok</em> <u>u</u></p>\n'
        assert views.exists('/gs/card') == false && views.exists('/gs/_card') && views.exists('/gs/pagina')
    }

    @Test
    void 'markdown: tabelas, tachado, autolink e conteudo seguro'() {
        String html = Markdown.toHtml('|a|b|\n|-|-|\n|1|2|\n\n~~x~~ https://grox.dev <img src=x onerror=y>')
        assert html.contains('<td>1</td>') && html.contains('<del>x</del>') && html.contains('<a rel="nofollow" href="https://grox.dev"')
        assert html.contains('&lt;img') && !html.contains('<img')
        assert Markdown.parse('---\na: 1\nb:\n  - x\n  - y\n---\ntexto').frontMatter == [a: '1', b: ['x', 'y']]
    }
}
