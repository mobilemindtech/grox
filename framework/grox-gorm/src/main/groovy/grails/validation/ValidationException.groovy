package grails.validation

import org.grox.data.validation.ValidationErrors

class ValidationException extends RuntimeException {

    final ValidationErrors errors

    ValidationException(String message, ValidationErrors errors) {
        super("${message}: ${errors}")
        this.errors = errors
    }
}
