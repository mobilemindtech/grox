package org.grox.i18n

import groovy.transform.CompileStatic

/**
 * Bean {@code messageSource} com a API do Spring, delegando ao {@link MessageSource} do Grox.
 * (Classe separada para não criar sobrecargas ambíguas {@code List}/{@code Object[]} com {@code null}.)
 */
@CompileStatic
class SpringMessageSource implements org.springframework.context.MessageSource {
    final MessageSource messages

    SpringMessageSource(MessageSource messages) { this.messages = messages }

    @Override
    /** Como no Spring: mensagem inexistente sem padrão devolve null (o g:message devolve o código). */
    String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        if (defaultMessage == null && (code == null || messages.find(code, locale) == null)) return null
        messages.getMessage(code, args == null ? null : args.toList(), defaultMessage, locale)
    }

    @Override
    String getMessage(String code, Object[] args, Locale locale) {
        messages.getMessage(code, args == null ? null : args.toList(), null, locale)
    }

    String getMessage(Object resolvable, Locale locale) {
        messages.getMessage(resolvable as MessageResolvable, locale)
    }
}
