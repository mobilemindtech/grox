package org.grox.data.validation

import org.grox.core.ClassUtils

/** {@code validate()}, {@code errors}, {@code hasErrors()} para entidades e command objects. */
trait ValidationSupport {

    private transient ValidationErrors validationErrors
    private transient List<FieldError> bindingErrors

    /** Erros de conversão do binding (typeMismatch): sobrevivem ao validate(). */
    void addBindingError(FieldError error) {
        if (bindingErrors == null) bindingErrors = []
        bindingErrors << error
        getErrors().add(error)
    }

    ValidationErrors getErrors() {
        if (validationErrors == null) validationErrors = new ValidationErrors(ClassUtils.userClass(this.getClass()).name)
        validationErrors
    }

    boolean hasErrors() {
        validationErrors != null && validationErrors.hasErrors()
    }

    void clearErrors() {
        validationErrors = null
        bindingErrors = null
    }

    boolean validate() {
        validate((List<String>) null)
    }

    boolean validate(List<String> fields) {
        ValidationErrors errors = getErrors()
        errors.clear()
        Map<String, Map<String, Object>> constraints = constraintsForValidation()
        if (bindingErrors) {
            bindingErrors.each { errors.add(it) }
            // o campo com erro de conversão não recebe também o erro de nullable
            constraints = constraints.findAll { k, v -> !bindingErrors.any { it.field == k } }
        }
        Validator.validate(this, constraints, errors, fields)
        !errors.hasErrors()
    }

    /** Constraints efetivas; entidades sobrescrevem para incluir os padrões do mapeamento. */
    Map<String, Map<String, Object>> constraintsForValidation() {
        Map<String, Map<String, Object>> declared = ConstraintsBuilder.declared(ClassUtils.userClass(this.getClass()))
        Map<String, Map<String, Object>> result = [:]
        // como no Grails: propriedades de command objects são obrigatórias por padrão
        for (MetaProperty mp : this.metaClass.properties) {
            if (mp.name in ['class', 'metaClass', 'errors', 'properties'] || !(mp instanceof MetaBeanProperty) || ((MetaBeanProperty) mp).setter == null) continue
            if (java.lang.reflect.Modifier.isStatic(mp.modifiers)) continue
            result[mp.name] = [nullable: false] + (declared[mp.name] ?: [:])
        }
        declared.each { k, v -> if (!result.containsKey(k)) result[k] = v }
        result
    }
}
