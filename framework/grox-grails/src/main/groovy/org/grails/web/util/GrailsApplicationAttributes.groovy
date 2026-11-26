package org.grails.web.util

import grails.core.ApplicationContext
import grails.core.GrailsApplication
import org.grox.http.FlashScope
import org.grox.web.WebRequest

/**
 * {@code grailsAttributes} de controllers e TagLibs do Grails, só com o que os projetos usam:
 * {@code messageSource}, {@code applicationContext}, {@code grailsApplication}, as URIs da
 * aplicação e do controller, e o flash.
 */
class GrailsApplicationAttributes {

    private final WebRequest webRequest

    GrailsApplicationAttributes(WebRequest webRequest) {
        this.webRequest = webRequest
    }

    GrailsApplication getGrailsApplication() {
        webRequest.ctx.application
    }

    ApplicationContext getApplicationContext() {
        webRequest.ctx.application.mainContext
    }

    Object getMessageSource() {
        getApplicationContext().getBean('messageSource')
    }

    /** Sem servlet container, o context path é sempre a raiz. */
    String getApplicationUri(Object request) {
        ''
    }

    String getControllerUri(Object request) {
        webRequest.controllerName ? '/' + webRequest.controllerName : ''
    }

    String getControllerActionUri(Object request) {
        getControllerUri(request) + (webRequest.actionName ? '/' + webRequest.actionName : '')
    }

    FlashScope getFlashScope(Object request) {
        webRequest.flash
    }
}
