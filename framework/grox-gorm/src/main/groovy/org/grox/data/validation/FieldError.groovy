package org.grox.data.validation

/**
 * Erro de validação de um campo. Estende o {@code FieldError} do Spring (compatibilidade), onde
 * ficam os campos: {@code objectName}, {@code field}, {@code rejectedValue}, {@code codes},
 * {@code arguments} e {@code defaultMessage}.
 */
class FieldError extends org.springframework.validation.FieldError {

    @Override
    String toString() {
        "FieldError(${objectName}.${field}: ${codes ? codes[0] : ''} = ${rejectedValue})"
    }
}
