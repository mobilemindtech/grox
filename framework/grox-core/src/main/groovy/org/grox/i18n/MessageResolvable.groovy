package org.grox.i18n

/**
 * Mensagem resolvida por códigos, do mais específico ao mais genérico, com argumentos e texto
 * padrão (como o {@code MessageSourceResolvable} do Spring). Os erros de validação do GORM a
 * implementam.
 */
interface MessageResolvable {
    List<String> getCodes()

    Object[] getArguments()

    String getDefaultMessage()
}
