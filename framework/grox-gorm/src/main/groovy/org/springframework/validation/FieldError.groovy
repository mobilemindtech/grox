package org.springframework.validation

/** Compatibilidade com o {@code FieldError} do Spring (o do Grox, {@code org.grox.data.validation.FieldError}, o estende). */
class FieldError extends ObjectError {
    String field
    Object rejectedValue
}
