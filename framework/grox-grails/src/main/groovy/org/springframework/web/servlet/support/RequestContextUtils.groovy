package org.springframework.web.servlet.support

import org.springframework.context.i18n.LocaleContextHolder

class RequestContextUtils {
    static Locale getLocale(Object request) {
        LocaleContextHolder.locale
    }
}
