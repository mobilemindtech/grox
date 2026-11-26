package org.grox.data.validation

/** Erros de validação de um objeto ({@code usuario.errors}), com a API usada nos apps Grails. */
class ValidationErrors implements org.springframework.validation.Errors {

    final String objectName
    final List<FieldError> allErrors = []

    ValidationErrors(String objectName) {
        this.objectName = objectName
    }

    boolean hasErrors() {
        !allErrors.isEmpty()
    }

    int getErrorCount() {
        allErrors.size()
    }

    List<FieldError> getFieldErrors() {
        allErrors.findAll { it.field != null }
    }

    List<FieldError> getGlobalErrors() {
        allErrors.findAll { it.field == null }
    }

    List<FieldError> getFieldErrors(String field) {
        allErrors.findAll { it.field == field }
    }

    FieldError getFieldError(String field) {
        allErrors.find { it.field == field }
    }

    FieldError getFieldError() {
        fieldErrors ? fieldErrors[0] : null
    }

    boolean hasFieldErrors(String field) {
        allErrors.any { it.field == field }
    }

    boolean hasFieldErrors() {
        !fieldErrors.isEmpty()
    }

    boolean hasGlobalErrors() {
        !globalErrors.isEmpty()
    }

    void rejectValue(String field, String code, Object[] args = null, String defaultMessage = null, Object rejected = null) {
        allErrors << new FieldError(objectName: objectName, field: field, rejectedValue: rejected,
                codes: [code], arguments: args ?: new Object[0], defaultMessage: defaultMessage)
    }

    void reject(String code, String defaultMessage = null) {
        allErrors << new FieldError(objectName: objectName, codes: [code], defaultMessage: defaultMessage)
    }

    void add(FieldError error) {
        allErrors << error
    }

    void clear() {
        allErrors.clear()
    }

    @Override
    String toString() {
        "${errorCount} erro(s) em ${objectName}: ${allErrors}"
    }
}
