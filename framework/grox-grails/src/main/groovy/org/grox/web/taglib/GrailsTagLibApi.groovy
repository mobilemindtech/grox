package org.grox.web.taglib

import org.grox.view.taglib.TagLibApi
import org.grox.web.WebRequest

/**
 * {@link TagLibApi} com o que é do Grails: {@code grailsApplication} e {@code grailsAttributes}. A AST o
 * aplica às classes {@code *TagLib} de {@code grails-app/taglib}.
 */
trait GrailsTagLibApi extends TagLibApi {

    grails.core.GrailsApplication getGrailsApplication() { (grails.core.GrailsApplication) getWebRequest()?.application }

    /** {@code grailsAttributes.messageSource}, {@code getControllerUri(request)}... */
    org.grails.web.util.GrailsApplicationAttributes getGrailsAttributes() {
        new org.grails.web.util.GrailsApplicationAttributes((WebRequest) getWebRequest())
    }
}
