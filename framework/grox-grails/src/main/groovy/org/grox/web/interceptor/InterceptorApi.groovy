package org.grox.web.interceptor

import grails.converters.JSON
import org.grox.core.ClassUtils
import org.grox.http.FlashScope
import org.grox.http.GroxParams
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession
import org.grox.web.ControllerSupport
import org.grox.web.RequestContext
import org.grox.web.WebRequest
import org.slf4j.Logger
import org.grox.core.Logs

/**
 * API dos interceptors do Grails 3+. Aplicado (AST) às classes {@code *Interceptor} em
 * {@code grails-app/controllers}. Sem {@code match(...)}, intercepta o controller de mesmo
 * nome ({@code LivroInterceptor} → {@code livro}).
 */
trait InterceptorApi implements Ordered {

    private final List<InterceptorMatcher> interceptorMatchers = []
    private String defaultControllerName

    InterceptorMatcher match(Map<String, Object> criteria) {
        def m = new InterceptorMatcher(criteria)
        interceptorMatchers << m
        m
    }

    InterceptorMatcher matchAll() {
        match([:])
    }

    boolean doesMatch(WebRequest wr) {
        if (interceptorMatchers.isEmpty()) {
            if (defaultControllerName == null) defaultControllerName = ClassUtils.logicalName(ClassUtils.userClass(this.getClass()), 'Interceptor')
            return wr.controllerName == defaultControllerName
        }
        interceptorMatchers.any { it.matches(wr) }
    }

    /** false interrompe a requisição (renderize ou redirecione antes). */
    boolean before() { true }

    /** false impede a renderização da view. {@code model} e {@code view} podem ser alterados aqui. */
    boolean after() { true }

    void afterView() {}

    /** Exceção da requisição corrente (ou null), como o {@code throwable} dos interceptors do Grails. */
    Throwable getThrowable() {
        def r = org.grox.web.RequestContext.currentOrNull()?.request
        (Throwable) (r?.getAttribute('exception') ?: r?.getAttribute('javax.servlet.error.exception'))
    }

    int getOrder() { 0 }

    WebRequest getWebRequest() { RequestContext.current() }

    GroxParams getParams() { RequestContext.current().params }

    GroxRequest getRequest() { RequestContext.current().request }

    GroxResponse getResponse() { RequestContext.current().response }

    GroxSession getSession() { RequestContext.current().session }

    FlashScope getFlash() { RequestContext.current().flash }

    String getControllerName() { RequestContext.current().controllerName }

    String getActionName() { RequestContext.current().actionName }

    Logger getLog() { Logs.of(this.getClass()) }

    /** Modelo da view pendente (ou o Map devolvido pela action). */
    Map getModel() {
        WebRequest wr = RequestContext.current()
        if (wr.pendingModel == null) wr.pendingModel = [:]
        wr.pendingModel
    }

    void setModel(Map model) { RequestContext.current().pendingModel = model }

    String getView() { RequestContext.current().pendingView }

    /**
     * {@code modelAndView} do Grails no {@code after()}: modelo e view da action (null se a action renderizou
     * outra coisa, como texto ou redirect). Alterar {@code model}/{@code viewName} altera a renderização.
     */
    ModelAndView getModelAndView() {
        RequestContext.current().pendingView != null ? new ModelAndView() : null
    }

    /** Visão do modelo e da view pendentes da requisição. */
    static class ModelAndView {
        Map getModel() { RequestContext.current().pendingModel }

        void setModel(Map model) { RequestContext.current().pendingModel = model }

        String getViewName() { RequestContext.current().pendingView }

        void setViewName(String view) { RequestContext.current().pendingView = view }
    }

    void setView(String view) {
        WebRequest wr = RequestContext.current()
        wr.pendingView = ControllerSupport.resolveView(wr, view)
        wr.response.committed = true
    }

    void render(Map args) { ControllerSupport.render(RequestContext.current(), args) }

    void render(CharSequence text) { ControllerSupport.writeText(RequestContext.current(), text, 'text/html') }

    void render(JSON json) { ControllerSupport.renderJson(RequestContext.current(), json) }

    void redirect(Map args) { ControllerSupport.redirect(RequestContext.current(), args) }

    void header(String name, Object value) { RequestContext.current().response.setHeader(name, value) }
}
