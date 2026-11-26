package org.grox.web.api

import grails.converters.JSON
import grails.async.Promise
import org.grox.async.AsyncSupport
import org.grox.data.validation.FieldError
import org.grox.http.FlashScope
import org.grox.http.GroxParams
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession
import org.grox.security.GroxUser
import org.grox.security.SecurityContextHolder
import org.grox.web.ControllerSupport
import org.grox.data.binding.DataBinder
import org.grox.web.RequestContext
import org.grox.web.WebRequest
import org.grox.view.taglib.TagNamespace
import org.slf4j.Logger
import org.grox.core.Logs

/**
 * API dos controllers do Grails. Aplicado automaticamente (AST) às classes
 * {@code *Controller} em {@code grails-app/controllers}.
 */
trait ControllerApi {

    WebRequest getWebRequest() { RequestContext.current() }

    GroxParams getParams() { RequestContext.current().params }

    GroxRequest getRequest() { RequestContext.current().request }

    GroxResponse getResponse() { RequestContext.current().response }

    GroxSession getSession() { RequestContext.current().session }

    FlashScope getFlash() { RequestContext.current().flash }

    String getControllerName() { RequestContext.current().controllerName }

    String getActionName() { RequestContext.current().actionName }

    grails.core.GrailsApplication getGrailsApplication() { RequestContext.current().ctx.application }

    /** {@code grailsAttributes.messageSource}, {@code getControllerUri(request)}... */
    org.grails.web.util.GrailsApplicationAttributes getGrailsAttributes() { new org.grails.web.util.GrailsApplicationAttributes(getWebRequest()) }

    Logger getLog() { Logs.of(this.getClass()) }

    void render(Map args) { ControllerSupport.render(RequestContext.current(), args) }

    void render(CharSequence text) { ControllerSupport.writeText(RequestContext.current(), text, 'text/html') }

    void render(Map args, CharSequence text) {
        Map a = new LinkedHashMap(args)
        a.text = text
        ControllerSupport.render(RequestContext.current(), a)
    }

    void render(JSON json) { ControllerSupport.renderJson(RequestContext.current(), json) }

    /** {@code render(404)}, {@code render(lista)}: escreve o texto do objeto, como no Grails. */
    void render(Object value) { ControllerSupport.writeText(RequestContext.current(), String.valueOf(value), 'text/html') }

    /**
     * Como no Grails: com {@code contentType} JSON, o closure monta o JSON
     * ({@code render(status: 401, contentType: 'application/json') { erro = 'x' }}); com XML, o {@code MarkupBuilder};
     * senão, HTML pela DSL de HTML ({@code render(layout: 'main') { h1 'Livros' }}, com as tags em {@code g.*}).
     */
    void render(Map args, Closure body) {
        Map a = new LinkedHashMap(args)
        String type = a.contentType?.toString()
        def wr = RequestContext.current()
        if (type?.contains('json')) {
            a.text = groovy.json.JsonOutput.toJson(org.grox.web.json.JsonClosureBuilder.build(body))
        } else if (type?.contains('xml')) {
            a.text = org.grox.view.html.HtmlRender.xml(body)
        } else {
            a.text = org.grox.view.html.HtmlRender.render(body, wr, wr.ctx.views, (Map) a.remove('model') ?: [:], a.remove('layout')?.toString())
            a.contentType = type ?: 'text/html'
        }
        ControllerSupport.render(wr, a)
    }

    void render(Closure body) { render([:], body) }

    void redirect(Map args) { ControllerSupport.redirect(RequestContext.current(), args) }

    void respond(Object value) { ControllerSupport.respond(RequestContext.current(), value, [:]) }

    /**
     * {@code respond obj, status: 201} (argumentos nomeados chegam primeiro) ou
     * {@code respond(obj, [status: 201])}.
     */
    void respond(Object first, Object second) {
        boolean namedFirst = first instanceof Map
        Map args = (Map) (namedFirst ? first : second)
        Object value = namedFirst ? second : first
        ControllerSupport.respond(RequestContext.current(), value, args)
    }

    Object withFormat(Closure blocks) { ControllerSupport.withFormat(RequestContext.current(), blocks) }

    void header(String name, Object value) { RequestContext.current().response.setHeader(name, value) }

    Object bindData(Object target, Object source) { DataBinder.bind(target, source) }

    Object bindData(Object target, Object source, Map options) { DataBinder.bind(target, source, options) }

    /** Tags como métodos: {@code g.createLink(...)}, {@code g.render(template: 'x')}. */
    TagNamespace getG() { RequestContext.current().ctx.views.tags.namespace('g') }

    /** Plugin rendering: {@code renderPdf(template: '/rel/pdf', model: [...], filename: 'r.pdf')} (template relativo a este controller). */
    boolean renderPdf(Map args) {
        def service = RequestContext.current().ctx.application.mainContext.containsBean('pdfRenderingService') ?
                RequestContext.current().ctx.application.mainContext.getBean('pdfRenderingService') : null
        if (service == null) throw new IllegalStateException('renderPdf: plugin rendering inativo (falta o flying-saucer-pdf no classpath)')
        service.render([controller: this] + args, RequestContext.current().response)
    }

    /** Tags {@code g:} chamadas direto na action, como no Grails: {@code formatDate(date: d, format: 'dd/MM')}. */
    Object methodMissing(String name, Object args) {
        def tags = RequestContext.CURRENT.bound ? RequestContext.current().ctx.views.tags : null
        if (tags?.has('g', name)) return tags.namespace('g').invokeMethod(name, args)
        throw new org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack(name, this.getClass(), (Object[]) args, false)   // sem stack: barato quando o Groovy tenta o delegate (builders)
    }

    /** Namespace de TagLib como propriedade ({@code auth.isAdmin()} com {@code static namespace = 'auth'}), como no Grails. */
    Object propertyMissing(String name) {
        def tags = RequestContext.CURRENT.bound ? RequestContext.current().ctx.views.tags : null
        if (tags?.hasNamespace(name)) return tags.namespace(name)
        throw new org.codehaus.groovy.runtime.metaclass.MissingPropertyExceptionNoStack(name, this.getClass())
    }

    /** {@code message(code: 'x', args: [..], default: '...')} ou {@code message(error: fieldError)}. */
    String message(Map attrs) {
        WebRequest wr = RequestContext.current()
        if (attrs.error instanceof FieldError) return wr.ctx.messages.getMessage((FieldError) attrs.error, wr.locale)
        wr.ctx.messages.getMessage(attrs.code?.toString(), attrs.args as List, attrs.default?.toString(), wr.locale)
    }

    /** Tarefa assíncrona (como {@code grails.async.Promises.task}); devolva-a da action. */
    Promise task(Closure body) { AsyncSupport.task(body) }

    /** Como o spring-security-core adiciona aos controllers. */
    boolean isLoggedIn() { SecurityContextHolder.authentication != null }

    GroxUser getPrincipal() { SecurityContextHolder.authentication?.principal }

    String createLink(Map attrs) { ControllerSupport.createLink(RequestContext.current(), attrs) }
}
