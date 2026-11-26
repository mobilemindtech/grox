package org.springframework.web.servlet.i18n

import org.grox.web.RequestContext

/**
 * Bean {@code localeResolver}: o locale da requisição do Grox ({@code ?lang=}, sessão, Accept-Language,
 * {@code grox.i18n.defaultLocale}).
 */
class SessionLocaleResolver {
    Locale defaultLocale

    Locale resolveLocale(Object request = null) {
        RequestContext.currentOrNull()?.locale ?: defaultLocale ?: Locale.default
    }

    /** Guarda o locale na sessão, como o {@code ?lang=}. */
    void setLocale(Object request, Object response, Locale locale) {
        def wr = RequestContext.currentOrNull()
        if (wr != null && locale != null) wr.getSession(true).setAttribute(org.grox.web.WebRequest.LOCALE_KEY, locale)
    }
}
