package org.springframework.validation

/** Compatibilidade com o {@code Errors} do Spring (implementado por {@code org.grox.data.validation.ValidationErrors}). */
interface Errors {
    boolean hasErrors()
    int getErrorCount()
    List<? extends FieldError> getFieldErrors()
    List<? extends FieldError> getFieldErrors(String field)
    FieldError getFieldError(String field)
    FieldError getFieldError()
    List<? extends FieldError> getGlobalErrors()
    boolean hasFieldErrors(String field)
    boolean hasFieldErrors()
    boolean hasGlobalErrors()
}
