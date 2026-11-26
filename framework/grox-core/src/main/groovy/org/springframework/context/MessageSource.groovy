package org.springframework.context

/** Compatibilidade: implementada por {@link org.grox.i18n.MessageSource} (bean {@code messageSource}). */
interface MessageSource {
    String getMessage(String code, Object[] args, String defaultMessage, Locale locale)
    String getMessage(String code, Object[] args, Locale locale)
}
