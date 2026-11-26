package org.springframework.validation

/** Compatibilidade com o {@code ObjectError} do Spring: erro de validação de um objeto. */
class ObjectError implements org.grox.i18n.MessageResolvable {
    String objectName
    /** Códigos de mensagem, do mais específico ao mais genérico. */
    List<String> codes = []
    Object[] arguments = new Object[0]
    String defaultMessage

    String getCode() {
        codes ? codes.last() : null
    }
}
