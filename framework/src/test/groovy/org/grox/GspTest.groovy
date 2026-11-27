package org.grox

import org.grox.view.GspViews
import org.grox.view.gsp.GspException
import org.grox.view.gsp.GspRenderContext
import org.grox.view.gsp.GspTemplateEngine
import org.grox.view.gsp.ViewLocator
import org.grox.view.taglib.RenderTagLib
import org.grox.view.taglib.TagLibApi
import org.grox.view.taglib.TagLibRegistry
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.assertThrows

class GspTest {

    @Test
    void 'compatibilidades do app C: each com var em expressao, ? final no in, each aninhado sem var, aspas no atributo'() {
        def m = [lista: [[filhos: [1, 2]], [filhos: [3]]], obj: [itens: ['a', 'b']]]
        assert render('/caso1', '<g:each in="${lista}"><g:each in="${it.filhos}">${it}</g:each>;</g:each>', m) == '12;3;'
        assert render('/caso2', '<g:each in="${obj?.itens?}" var="${x}" status="i">${i}${x}</g:each>', m) == '0a1b'
        assert new org.grox.http.SafeHtml('ABC').toLowerCase() == 'abc'   // retorno de tag como método: métodos de String
        assert render('/caso3', '''<g:set var="v" value='[a: m ?: '']'/>${v.a}''', [m: 'ok']) == 'ok'
    }


    /** TagLib de teste (fora de grails-app, então implementa o trait explicitamente). */
    static class TesteTagLib implements TagLibApi {
        static namespace = 't'
        static returnObjectForTags = ['soma']

        def caixa = { attrs, body ->
            out << "<div title=\"${attrs.titulo}\">" << body() << '</div>'
        }
        def repetir = { attrs, body ->
            (1..(attrs.vezes as int)).each { out << body(it) }
        }
        def comVars = { attrs, body ->
            out << body([nome: 'Bia', idade: 7])
        }
        def upper = { attrs ->
            out << attrs.texto.toString().toUpperCase()
        }
        def soma = { attrs ->
            (attrs.a as int) + (attrs.b as int)
        }
    }

    /** Tags como métodos (Grails 3+), misturadas com closure na mesma TagLib. */
    static class MetodoTagLib implements TagLibApi {
        static namespace = 'm'
        static returnObjectForTags = ['dobro']

        def caixa(Map attrs, Closure body) {
            out << "<p class=\"${attrs.cls}\">" << body() << '</p>'
        }
        // sobrecarga: vale a que recebe o corpo
        def item(Map attrs) { out << 'sem corpo' }
        def item(Map attrs, body) { out << '<li>' << body() << '</li>' }
        def dobro(Map attrs) { (attrs.n as int) * 2 }
        def lista(Map attrs) {
            // chama outra tag-método da mesma TagLib e uma tag-closure
            attrs.itens.each { v -> item([:]) { v } }
            out << estrela([:])
        }
        def estrela = { attrs -> '*' }
        def falha(Map attrs) { throw new IllegalStateException('falhou') }
        // não são tags: auxiliar com outra assinatura, estático e privado
        String ajuda(String s) { s }
        static String fixo(Map attrs) { 'x' }
        private String oculto(Map attrs) { 'x' }
    }

    static class MetodoFilhaTagLib extends MetodoTagLib {
        static namespace = 'mf'
        @Override
        def dobro(Map attrs) { (attrs.n as int) * 3 }
    }

    @TempDir
    static File dir

    static GspTemplateEngine engine
    static GspViews views

    @BeforeAll
    static void setup() {
        def tags = new TagLibRegistry()
        tags.register(new RenderTagLib())
        tags.register(new TesteTagLib())
        tags.register(new MetodoTagLib())
        tags.register(new MetodoFilhaTagLib())
        engine = new GspTemplateEngine(new ViewLocator(dir, GspTest.classLoader), true, tags, GspTest.classLoader)
        views = new GspViews(engine, tags, null)
    }

    static void write(String path, String gsp) {
        def f = new File(dir, path + '.gsp')
        f.parentFile.mkdirs()
        long before = f.exists() ? f.lastModified() : 0
        f.text = gsp
        f.setLastModified(Math.max(System.currentTimeMillis(), before + 2000))
    }

    static String render(String path, String gsp, Map model = [:]) {
        if (gsp != null) write(path, gsp)
        def sw = new StringWriter()
        def rc = new GspRenderContext(null, views, sw)
        rc.bind { engine.render(engine.find(path), model, rc) }
        sw.toString()
    }

    @Test
    void 'encodeAsHTML e encodeAsJavaScript nao sao codificados de novo pelo ${} nem pelas tags'() {
        def req = new org.grox.http.GroxRequest(method: 'GET', path: '/', uri: '/')
        def scope = [getRequest: { req }, getResponse: { null }, getLocale: { Locale.ROOT }, getSession: { boolean b -> null }] as org.grox.http.RequestScope
        org.grox.http.CurrentRequest.with(scope) {
            // antes: &amp;lt;b&amp;gt;&amp;amp; (escapado duas vezes)
            assert render('/enc1', '${x.encodeAsHTML()}|${x}', [x: '<b>&']) == '&lt;b&gt;&amp;|&lt;b&gt;&amp;'
            // as tags escapam atributos com o Html.escape, que também reconhece o que já foi codificado
            assert org.grox.http.Html.escape('a&b'.encodeAsHTML()) == 'a&amp;b' && org.grox.http.Html.escape('a&b') == 'a&amp;b'
            // JavaScript: sem aspas nem < > & crus, então vai como está até dentro de atributo
            String js = render('/enc3', "<a onclick=\"f('\${x.encodeAsJavaScript()}')\">", [x: 'D\'Agua "&" </script>'])
            assert js == "<a onclick=\"f('D\\u0027Agua \\u0022\\u0026\\u0022 \\u003C/script\\u003E')\">"
            assert render('/enc4', '${x.encodeAsURL()}', [x: 'a b&c']) == 'a+b%26c'
        }
        // fora de uma requisição, nada é registrado: o ${} codifica normalmente
        assert render('/enc1', null, [x: '<b>']) == '&amp;lt;b&amp;gt;|&lt;b&gt;'
    }

    @Test
    void 'tags como metodos: corpo, sobrecarga, returnObject, mistura com closure, heranca e excecao'() {
        assert render('/m1', '<m:caixa cls="a">oi ${n}</m:caixa>', [n: 1]) == '<p class="a">oi 1</p>'
        assert render('/m2', '<m:item>x</m:item>|${m.dobro(n: 4) + 1}|<m:dobro n="5"/>') == '<li>x</li>|9|10'
        assert render('/m3', '<m:lista itens="${[1, 2]}"/>') == '<li>1</li><li>2</li>*'
        assert render('/m4', '<mf:dobro n="5"/>|<mf:caixa cls="b">c</mf:caixa>') == '15|<p class="b">c</p>'
        def tags = new TagLibRegistry()
        tags.register(new MetodoTagLib())
        assert tags.tagsOf('m').keySet() == ['caixa', 'item', 'dobro', 'lista', 'estrela', 'falha'] as Set
        def e = org.junit.jupiter.api.Assertions.assertThrows(Exception) { render('/m5', '<m:falha/>') }
        while (e != null && !(e instanceof IllegalStateException)) e = e.cause
        assert e?.message == 'falhou'
    }

    @Test
    void 'codecs de digest, Base64 e Hex do Grails'() {
        assert 'abc'.encodeAsMD5() == '900150983cd24fb0d6963f7d28e17f72'
        assert 'abc'.encodeAsSHA1() == 'a9993e364706816aba3e25717850c26c9cd0d89d'
        assert 'abc'.encodeAsSHA256() == 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
        assert 'abc'.bytes.encodeAsMD5() == 'abc'.encodeAsMD5() && 'abc'.encodeAsMD5Bytes().length == 16
        assert 'abc'.encodeAsBase64() == 'YWJj' && 'abc'.encodeAsHex() == '616263'
        assert 123L.toString().encodeAsMD5()[0..5] == '202cb9'   // uso do app C: str.encodeAsMD5()[0..5]
    }

    @Test
    void 'expressoes sao codificadas em HTML, raw nao'() {
        assert render('/e1', 'Olá ${nome}! ${html} ${raw(html)} ${nada}.', [nome: 'Ana', html: '<b>&</b>']) ==
                'Olá Ana! &lt;b&gt;&amp;&lt;/b&gt; <b>&</b> .'
        assert render('/e2', '<%= "<i>" %>|${[1, 2].collect { it * 2 }}|${"x${1 + 1}"}') == '&lt;i&gt;|[2, 4]|x2'
    }

    @Test
    void 'scriptlets, comentarios e diretivas'() {
        String gsp = '''<%@ page import="java.time.LocalDate" %>%{-- some --}%<%-- também --%><% def x = 21 %>${x * 2} ${LocalDate.of(2026, 9, 28)}<% for (i in 1..3) { %>${i}<% } %>'''
        assert render('/s1', gsp) == '42 2026-09-28123'
    }

    @Test
    void 'if, elseif, else e unless'() {
        String gsp = '''<g:if test="${n > 10}">grande</g:if>
<g:elseif test="${n > 5}">médio</g:elseif>
<g:else>pequeno</g:else>|<g:unless test="${n}">zero</g:unless>'''
        assert render('/if1', gsp, [n: 20]) == 'grande|'
        assert render('/if1', null, [n: 7]) == 'médio|'
        assert render('/if1', null, [n: 0]) == 'pequeno|zero'
    }

    @Test
    void 'each com var, status, aninhado, mapa e lista nula'() {
        String gsp = '''<g:each in="${lista}" var="x" status="i">${i}:${x}<g:each in="${[1, 2]}">(${it})</g:each>,</g:each>|<g:each in="${mapa}" var="e">${e.key}=${e.value};</g:each>|<g:each in="${nada}">nunca</g:each>'''
        assert render('/each1', gsp, [lista: ['a', 'b'], mapa: [k: 1, j: 2]]) == '0:a(1)(2),1:b(1)(2),|k=1;j=2;|'
    }

    @Test
    void 'set em pageScope, com valor e com corpo'() {
        String gsp = '''<g:set var="total" value="${2 + 3}"/><g:set var="bloco">b${total}</g:set>${total}-${bloco}-${pageScope.total}'''
        assert render('/set1', gsp) == '5-b5-5'
    }

    @Test
    void 'tags customizadas: atributos, corpo, body(item), body(vars) e chamada como metodo'() {
        assert render('/t1', '<t:caixa titulo="T${n}">corpo ${n}</t:caixa>', [n: 1]) == '<div title="T1">corpo 1</div>'
        assert render('/t2', '<t:repetir vezes="${3}">[${it}]</t:repetir>') == '[1][2][3]'
        assert render('/t3', '<t:comVars>${nome}/${idade}</t:comVars>') == 'Bia/7'
        assert render('/t4', '${t.upper(texto: "abc")} <t:caixa titulo="x"><t:upper texto="dentro"/></t:caixa>') == 'ABC <div title="x">DENTRO</div>'
        assert render('/t5', '${t.soma(a: 2, b: 3) * 10} <t:soma a="1" b="1"/>') == '50 2'
        assert render('/t6', '''${t.caixa(titulo: 'x') { 'corpo' }}|${t.caixa([titulo: 'y'], 'texto')}''') == '<div title="x">corpo</div>|<div title="y">texto</div>'
    }

    @Test
    void 'templates com model, bean e collection'() {
        write('/parts/_item', '<li>${i}</li>')
        assert render('/tpl1', '<ul><g:render template="/parts/item" collection="${[1, 2]}" var="i"/></ul>') == '<ul><li>1</li><li>2</li></ul>'
        assert render('/tpl2', '''<g:render template="/parts/item" model="${[i: 'x']}"/>''') == '<li>x</li>'
        write('/parts/_bean', '[${it.n}]')
        assert render('/tpl3', '<g:render template="/parts/bean" bean="${[n: 9]}"/>') == '[9]'
    }

    @Test
    void 'layout SiteMesh com meta layout, pageProperty e sitemesh write'() {
        write('/layouts/base', '''<html><head><title>[<g:layoutTitle/>]</title><g:layoutHead/></head><body class="l"><nav><g:pageProperty name="page.menu"/></nav><g:layoutBody/><sitemesh:write property="meta.autor"/></body></html>''')
        String page = render('/pg', '''<html><head><title>Página</title><meta name="layout" content="base"/><meta name="autor" content="Rê"/></head><body class="p"><content tag="menu">M</content><p>${msg}</p></body></html>''', [msg: 'oi'])
        def rc = new GspRenderContext(null, views, new StringWriter())
        String out = rc.bind { views.layouts.decorate(page, [:], rc) }
        assert out.contains('<title>[Página]</title>')
        assert out.contains('<meta name="autor" content="Rê"/>')
        assert out.contains('<body class="l p">')
        assert out.contains('<nav>M</nav>')
        assert out.contains('<p>oi</p>')
        assert out.endsWith('Rê</body></html>')
        // nome de bloco com ponto, como no layout do app A
        write('/layouts/pontos', '''<html><head><title><g:layoutTitle default="Sem"/></title></head><body><g:ifPageProperty name="page.include.bottom">[B]</g:ifPageProperty><g:ifPageProperty name="page.nada">[N]</g:ifPageProperty><g:layoutBody/><g:if test="${ pageProperty(name:'page.include.bottom') }"><g:pageProperty name="page.include.bottom"/></g:if><i><g:pageProperty name="page.include.naoExiste" default="-"/></i></body></html>''')
        String comPonto = render('/pg2', '''<html><head><meta name="layout" content="pontos"/></head><body><p>corpo</p><content tag="include.bottom"><script src="/assets/frontend/suporte.js"></script></content></body></html>''')
        String out2 = rc.bind { views.layouts.decorate(comPonto, [:], rc) }
        assert out2.contains('<p>corpo</p><script src="/assets/frontend/suporte.js"></script><i>-</i>')
        assert out2.contains('<title>Sem</title>') && out2.contains('<body>[B]<p>corpo</p>') && !out2.contains('[N]')
        // sem layout: devolve a página intacta
        String semLayout = '<html><head><title>x</title></head><body>y</body></html>'
        assert rc.bind { views.layouts.decorate(semLayout, [:], rc) } == semLayout
    }

    @Test
    void 'campos de formulario e tags de asset escritos como metodos'() {
        def tags = new TagLibRegistry()
        tags.register(new org.grox.view.taglib.FormTagLib())
        tags.register(new org.grox.plugins.assetpipeline.AssetTagLib())
        def eng = new GspTemplateEngine(new ViewLocator(dir, GspTest.classLoader), true, tags, GspTest.classLoader)
        write('/campos', '<g:hiddenField name="id" value="${7}"/>|<g:passwordField name="senha"/>|<g:emailField name="e" value="${u}"/>|' +
                '<g:numberField name="n" id="x"/>|<g:dateField name="d"/>|<g:field type="color" name="c"/>|<g:submitButton name="ok"/>|' +
                '<asset:image src="logo.png" alt="L"/>|<asset:javascript src="app"/>|<asset:stylesheet src="/app.css"/>|<asset:link rel="icon" href="f.ico"/>')
        def sw = new StringWriter()
        def rc = new GspRenderContext(null, new GspViews(eng, tags, null), sw)
        rc.bind { eng.render(eng.find('/campos'), [u: 'a"b'], rc) }
        assert sw.toString().tokenize('|') == ['<input type="hidden" name="id" value="7" />', '<input type="password" name="senha" id="senha" />',
                '<input type="email" name="e" value="a&quot;b" id="e" />', '<input type="number" name="n" id="x" />',
                '<input type="date" name="d" id="d" />', '<input type="color" name="c" id="c" />', '<input type="submit" name="ok" value="ok" />',
                '<img src="/assets/logo.png" alt="L"/>', '<script src="/assets/app.js"></script>', '<link rel="stylesheet" href="/assets/app.css"/>',
                '<link href="/assets/f.ico" rel="icon"/>']
    }

    @Test
    void 'reload quando o arquivo muda'() {
        assert render('/r1', 'v1') == 'v1'
        assert render('/r1', 'v2') == 'v2'
    }

    @Test
    void 'erros indicam a linha da GSP'() {
        def unclosed = assertThrows(GspException) { render('/err1', 'a\n<t:caixa titulo="x">\nb') }
        assert unclosed.line == 2 && unclosed.message.contains('não foi fechada')

        def mismatch = assertThrows(GspException) { render('/err2', '<g:if test="${true}">\n</g:each>') }
        assert mismatch.line == 2

        def compile = assertThrows(GspException) { render('/err3', 'linha1\nlinha2 ${ 1 + }\nlinha3') }
        assert compile.line == 2 && compile.message.contains('erro de compilação')

        def runtime = assertThrows(GspException) { render('/err4', 'ok\nok\n${nada.metodo()}') }
        assert runtime.line == 3 && runtime.message.contains('NullPointerException')

        def unknown = assertThrows(GspException) { render('/err5', '<t:naoExiste/>') }
        assert unknown.message.contains('<t:naoExiste>')
    }

    @Test
    void 'compatibilidades do piloto: literal de lista e mapa, g:if env, template com o modelo da pagina, caminho relativo'() {
        write('/pil/_item', 'item=${valor}')
        String out = render('/pil/pagina', '''<t:upper texto="[a: 1]"/>|<g:if env="naoexiste">nao</g:if><g:if env="${grails.util.Environment.currentName}">sim</g:if>|<g:render template="/pil/item"/>|<g:render template="/outra/../pil/item"/>|<t:caixa titulo="x"<b>y</b></t:caixa>''', [valor: 42])
        assert out == '[A:1]|sim|item=42|item=42|<div title="x"><b>y</b></div>'
    }

    /** Requisição de views sem o módulo Grails: a GSP só conhece o {@link org.grox.view.ViewRequest}. */
    static class RequisicaoSimples implements org.grox.view.ViewRequest {
        org.grox.http.GroxRequest request = new org.grox.http.GroxRequest(method: 'GET', path: '/x', uri: '/x')
        org.grox.http.GroxResponse response = new org.grox.http.GroxResponse()
        Locale locale = Locale.forLanguageTag('pt-BR')
        org.grox.http.GroxParams params = new org.grox.http.GroxParams()
        org.grox.http.FlashScope flash = new org.grox.http.FlashScope()
        String controllerName = 'livro'
        String actionName = 'show'
        Class<?> controllerClass = null
        org.grox.routing.LinkGenerator links
        org.grox.i18n.MessageSource messages = new org.grox.i18n.MessageSource(GspTest.classLoader, 'messages', Locale.forLanguageTag('pt-BR'))
        org.grox.http.StaticResources statics = null
        GspViews views
        Object application = null

        org.grox.http.GroxSession getSession(boolean create) { null }

        org.grox.http.GroxSession getSession() { null }
    }

    @Test
    void 'pagina completa com ViewRequest proprio, sem WebRequest nem Grails'() {
        def tags = new TagLibRegistry()
        tags.register(new org.grox.view.taglib.ApplicationTagLib())
        def eng = new GspTemplateEngine(new ViewLocator(dir, GspTest.classLoader), true, tags, GspTest.classLoader)
        def v = new GspViews(eng, tags, null)
        def req = new RequisicaoSimples(views: v,
                links: new org.grox.routing.LinkGenerator(org.grox.routing.UrlMappingsHolder.build([]), [:], null))
        req.params.q = 'x'
        write('/simples/show', '${controllerName}/${actionName} ${params.q} <g:message code="nao.existe" default="padrao"/> <g:createLink action="edit" id="3"/>')
        v.render('/simples/show', [:], req)
        assert req.response.text == 'livro/show x padrao /livro/edit/3'
        assert req.response.contentType == 'text/html'
    }

    @Test
    void 'imports padrao das GSPs vindos dos modulos'() {
        def anterior = GspTemplateEngine.imports
        try {
            GspTemplateEngine.imports = ['java.util.concurrent.TimeUnit']
            def eng = new GspTemplateEngine(new ViewLocator(dir, GspTest.classLoader), true, new TagLibRegistry(), GspTest.classLoader)
            write('/imports', '${TimeUnit.SECONDS.toMillis(2)}')
            def sw = new StringWriter()
            def rc = new GspRenderContext(null, views, sw)
            rc.bind { eng.render(eng.find('/imports'), [:], rc) }
            assert sw.toString() == '2000'
        } finally {
            GspTemplateEngine.imports = anterior
        }
        assert org.grox.view.GspModule.providedImports([new org.grox.web.GrailsModule()]) == ['grails.converters.JSON']
    }

    @Test
    void 'DSL de HTML: elementos vazios, escape, SafeHtml, atributos null e doctype'() {
        String html = org.grox.view.html.HtmlRender.render({
            html {
                head { meta(charset: 'utf-8'); script(src: '/app.js') }
                body {
                    h1 'Livros & <Cia>'
                    p(class: null, id: 'x', new org.grox.http.SafeHtml('<b>já seguro</b>'))
                    input(name: 'q', value: '"aspas"')
                    br()
                    div(class: 'vazio')
                }
            }
        }, null, null)
        assert html == '<!DOCTYPE html><html><head><meta charset="utf-8" /><script src="/app.js"></script></head>' +
                '<body><h1>Livros &amp; &lt;Cia&gt;</h1><p id="x"><b>já seguro</b></p><input name="q" value="&quot;aspas&quot;" />' +
                '<br /><div class="vazio"></div></body></html>'
    }

    @Test
    void 'DSL de HTML com tags g e layout SiteMesh'() {
        write('/layouts/simples', '<html><head><title><g:layoutTitle/></title></head><body>[<g:layoutBody/>]</body></html>')
        def livros = [[titulo: 'A'], [titulo: 'B']]
        String html = org.grox.view.html.HtmlRender.render({
            html {
                head { title 'Lista'; meta(name: 'layout', content: 'simples') }
                body { ul { livros.each { l -> li l.titulo } }; p t.soma(a: 1, b: 2) }
            }
        }, null, views)
        assert html.contains('<title>Lista</title>') && html.contains('[<ul><li>A</li><li>B</li></ul><p>3</p>]')
        String fragmento = org.grox.view.html.HtmlRender.render({ div 'só o corpo' }, null, views, [:], 'simples')
        assert fragmento.contains('[<div>só o corpo</div>]')
    }
}
