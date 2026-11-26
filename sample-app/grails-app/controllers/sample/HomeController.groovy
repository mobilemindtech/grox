package sample

import extra.NotaService
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.annotation.Secured
import jakarta.servlet.http.Cookie
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.security.web.WebAttributes
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import jakarta.servlet.http.HttpServletRequest
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest

class HomeController {

    // um ator novo por requisição (o padrão da aplicação é 'pool')
    static concurrency = 'request'

    SaudacaoService saudacaoService
    UsuarioService usuarioService
    EstatisticaService estatisticaService
    NotaService notaService   // da biblioteca sample-lib (org.grox.lib)

    def index() {
        [thread: Thread.currentThread().toString(), agora: new Date(), total: usuarioService.contar(),
         estatisticas: estatisticaService.resumo()]
    }

    def ola(String nome) {
        render text: saudacaoService.saudar(nome ?: 'mundo'), contentType: 'text/plain'
    }

    /** Action como propriedade closure (estilo Grails 2, ainda aceito pelo Grails). */
    def fechamento = {
        render text: "closure ${params.x} ${saudacaoService.saudar('Bia')}", contentType: 'text/plain'
    }

    def olaParams() {
        render text: saudacaoService.saudarPorParams(params), contentType: 'text/plain'
    }

    /** {@code grailsAttributes} no controller e numa TagLib (view home/atributos.gsp). */
    def atributos() {
        [uri: grailsAttributes.getControllerUri(request) + grailsAttributes.getApplicationUri(request)]
    }

    /** Namespace de TagLib como propriedade do controller ({@code fmt.idade}), como no Grails. */
    def tagNamespace() {
        render text: "${fmt.idade(valor: 30)}|${fmt.rotulo(valor: 30)}", contentType: 'text/plain'
    }

    /** Artefatos de uma biblioteca org.grox.lib: service, domain, TagLib e mensagens dela. */
    def nota() {
        notaService.anotar(params.texto ?: 'x')
        render text: "${nota.total()}|${message(code: 'home.titulo')}", contentType: 'text/plain'
    }

    def pdfRenderingService

    /** Plugin rendering: PDF de um template GSP (XHTML) direto na resposta. */
    def pdf() {
        pdfRenderingService.render([template: 'relatorio', controller: this, model: [titulo: 'Vendas', itens: ['a', 'b']],
                                    filename: 'relatorio.pdf'], response)
    }

    /** O mesmo pelo método do controller ({@code renderPdf}), devolvendo os bytes por render(). */
    def pdfBytes() {
        def png = new ByteArrayOutputStream()
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), 'png', png)
        // logo: imagem embutida por <rendering:inlinePng>
        def out = (ByteArrayOutputStream) pdfRenderingService.render(template: '/home/relatorio', model: [titulo: 'Bytes', itens: [], logo: png.toByteArray()])
        render text: "${out.size() > 0 && new String(out.toByteArray(), 0, 4) == '%PDF'}", contentType: 'text/plain'
    }

    def pdfMetodo() {
        renderPdf(template: 'relatorio', model: [titulo: 'Método', itens: ['x']])
    }

    def springSecurityService
    def grailsResourceLocator
    def consoleService
    def authenticationTrustResolver

    /** Cookies como na Servlet API: request.cookies (Cookie[]), response.addCookie, g.cookie. */
    def cookies() {
        def a = request.cookies.find { it.name == 'a' }
        response.addCookie(new Cookie('b', '2').tap { maxAge = 60; httpOnly = true })
        render text: "${a?.value}|${g.cookie(name: 'a')}", contentType: 'text/plain'
    }

    /** Integração com o spring-security-core usada por LoginController próprios (como o do app C). */
    def seguranca() {
        def auth = SCH.context.authentication
        def saved = new HttpSessionRequestCache().getRequest(request, response)
        def falha = session[WebAttributes.AUTHENTICATION_EXCEPTION]
        render text: [auth?.name, authenticationTrustResolver.isAnonymous(auth), saved?.requestURI, saved?.queryString,
                      falha?.getClass()?.simpleName, session[SpringSecurityUtils.SPRING_SECURITY_LAST_USERNAME_KEY],
                      SpringSecurityUtils.securityConfig.apf.filterProcessesUrl, SpringSecurityUtils.securityConfig.successHandler.defaultTargetUrl,
                      springSecurityService.isAjax(request), SpringSecurityUtils.securityConfig.nao.existe ? 'x' : 'vazio',
                      authenticatedUser?.username].join('|'),
                contentType: 'text/plain'
    }

    /** Usuários logados (sessões autenticadas ativas), como o sessionRegistry do Spring Security. */
    def logados() {
        render text: new org.springframework.security.core.session.SessionRegistryImpl().allPrincipals*.username.sort().unique().join(','), contentType: 'text/plain'
    }

    /** SCH.clearContext() desloga (como o LogoutController do app C). */
    def sair() {
        SCH.clearContext()
        render text: "${SCH.context.authentication == null}", contentType: 'text/plain'
    }

    /** grailsResourceLocator (assets/public) e consoleService.eval (web-console). */
    def recursos() {
        def r = grailsResourceLocator.findResourceForURI('/images/logo.svg')
        def ev = consoleService.eval('println "oi"; 1 + 1', true, [:])
        render text: "${r?.exists()}|${r?.inputStream?.text?.contains('<svg')}|${ev.result}|${ev.output.trim()}", contentType: 'text/plain'
    }

    /** View com modelo alterado pelo MavInterceptor (modelAndView). */
    def mav() {
        [a: 1]
    }

    /** A action roda com o classloader da aplicação como context classloader (bibliotecas que varrem pacotes). */
    def classLoaderContexto() {
        render text: Thread.currentThread().contextClassLoader.is(getClass().classLoader).toString(), contentType: 'text/plain'
    }

    /** Tag g: chamada direto na action. */
    def tagDireta() {
        render text: formatDate(date: new Date(0L), format: 'yyyy', timeZone: TimeZone.getTimeZone('UTC')), contentType: 'text/plain'
    }

    /** API servlet/Spring usada por apps Grails fora dos controllers (como o CustomTenantResolver do app B). */
    def servlet() {
        HttpServletRequest req = ((ServletWebRequest) RequestContextHolder.getRequestAttributes()).getRequest()
        if (req.getParameter('tenant')) req.getSession().setAttribute(SessionTenantResolver.ATTRIBUTE, req.getParameter('tenant'))
        def web = GrailsWebRequest.lookup()
        render text: "${new SessionTenantResolver().resolveTenantIdentifier()}|${web.controllerName}/${web.actionName}|${req.getRequestURL().contains('/home/servlet')}",
                contentType: 'text/plain'
    }

    /** DSL de HTML no controller: {@code render { }} sem contentType é HTML (MarkupBuilder), como no Grails. */
    def htmlDsl() {
        render {
            div(id: 'saudacao') {
                p "Olá ${params.nome}"
                a(href: g.createLink(controller: 'home', action: 'index'), 'início')
            }
        }
    }

    /** {@code request.withFormat} como nos scaffolds: bloco pelo Content-Type da requisição. */
    def formato() {
        request.withFormat {
            form { render text: 'form', contentType: 'text/plain' }
            json { render text: 'json', contentType: 'text/plain' }
            '*' { render text: 'outro', contentType: 'text/plain' }
        }
    }

    /** {@code request.JSON} é um {@code JSONObject} do Grails (gancho do módulo Grails sobre o http do core). */
    def corpoJson() {
        def json = request.JSON
        render text: "${json.getClass().simpleName}|${json.optString('nome', '?')}|${json.optString('falta', 'padrao')}", contentType: 'text/plain'
    }

    def contador() {
        session.visitas = (session.visitas ?: 0) + 1
        render contentType: 'application/json', text: """{"visitas": ${session.visitas}}"""
    }

    def mensagem() {
        flash.message = "Olá do flash"
        redirect action: 'lerMensagem'
    }

    def lerMensagem() {
        render text: flash.message ?: 'vazio', contentType: 'text/plain'
    }

    /** @Secured tem precedência sobre a regra '/home/**' (permitAll) do application.yml. */
    @Secured('ROLE_ADMIN')
    def painel() {
        render text: "Painel de ${principal.username}", contentType: 'text/plain'
    }

    /** O InputStream do render(file:) é fechado (os testes conferem). */
    static volatile boolean arquivoFechado

    def arquivo() {
        def input = new ByteArrayInputStream('conteudo do arquivo'.bytes) {
            @Override
            void close() {
                super.close()
                HomeController.arquivoFechado = true
            }
        }
        render file: input, fileName: 'relatório.txt', contentType: 'text/plain'
    }

    /** Simula uma action demorada (usada no teste de desligamento gracioso). */
    def lento(Integer ms) {
        Thread.sleep(Math.min(ms ?: 500, 5000))
        render text: 'terminou', contentType: 'text/plain'
    }

    def falha() {
        throw new IllegalStateException('falha proposital')
    }
}
