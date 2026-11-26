package org.grails.web.util

import groovy.transform.CompileStatic
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletWebRequest

@CompileStatic
class WebUtils {
    static ServletWebRequest retrieveGrailsWebRequest() {
        (ServletWebRequest) RequestContextHolder.currentRequestAttributes()
    }
}
