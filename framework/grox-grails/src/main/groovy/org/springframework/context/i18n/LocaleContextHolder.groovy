package org.springframework.context.i18n

import groovy.transform.CompileStatic
import org.grox.web.RequestContext

/** Locale da requisição corrente (Accept-Language / param lang), ou o padrão da JVM. */
@CompileStatic
class LocaleContextHolder {
    static Locale getLocale() {
        RequestContext.currentOrNull()?.locale ?: Locale.default
    }
}
