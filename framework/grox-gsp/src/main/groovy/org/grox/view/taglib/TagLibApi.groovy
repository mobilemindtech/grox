package org.grox.view.taglib

import org.grox.http.FlashScope
import org.grox.http.GroxParams
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession
import org.grox.view.gsp.GspRenderContext
import org.grox.view.ViewRequest
import org.slf4j.Logger
import org.grox.core.Logs

/**
 * API das TagLibs do Grails ({@code out}, {@code params}, {@code g.*}...). Aplicado
 * automaticamente (AST) às classes {@code *TagLib} em {@code grails-app/taglib}.
 */
trait TagLibApi {

    @groovy.transform.CompileStatic
    Writer getOut() { GspRenderContext.current().out }

    @groovy.transform.CompileStatic
    GspRenderContext getRenderContext() { GspRenderContext.current() }

    @groovy.transform.CompileStatic
    ViewRequest getWebRequest() { GspRenderContext.current().webRequest }

    @groovy.transform.CompileStatic
    GroxParams getParams() { getWebRequest()?.params }

    @groovy.transform.CompileStatic
    GroxRequest getRequest() { getWebRequest()?.request }

    @groovy.transform.CompileStatic
    GroxResponse getResponse() { getWebRequest()?.response }

    @groovy.transform.CompileStatic
    GroxSession getSession() { getWebRequest()?.session }

    @groovy.transform.CompileStatic
    FlashScope getFlash() { getWebRequest()?.flash }

    @groovy.transform.CompileStatic
    String getControllerName() { getWebRequest()?.controllerName }

    @groovy.transform.CompileStatic
    String getActionName() { getWebRequest()?.actionName }

    @groovy.transform.CompileStatic
    Binding getPageScope() { GspRenderContext.current().pageScope }

    @groovy.transform.CompileStatic
    TagNamespace getG() { GspRenderContext.current().tags.namespace('g') }

    Logger getLog() { Logs.of(this.getClass()) }

    /** Namespace de outra TagLib como propriedade ({@code auth.isAdmin()}), como no Grails. */
    Object propertyMissing(String name) {
        TagLibRegistry tags = GspRenderContext.CURRENT.bound ? GspRenderContext.current().tags : null
        if (tags?.hasNamespace(name)) return tags.namespace(name)
        throw new org.codehaus.groovy.runtime.metaclass.MissingPropertyExceptionNoStack(name, this.getClass())
    }

    /** Tags {@code g:} chamadas diretamente: {@code createLink(action: 'x')}. */
    Object methodMissing(String name, Object args) {
        TagLibRegistry tags = GspRenderContext.current().tags
        if (tags.has('g', name)) return tags.namespace('g').invokeMethod(name, args)
        throw new org.codehaus.groovy.runtime.metaclass.MissingMethodExceptionNoStack(name, this.getClass(), (Object[]) args, false)   // sem stack: barato quando o Groovy tenta o delegate (builders)
    }
}
