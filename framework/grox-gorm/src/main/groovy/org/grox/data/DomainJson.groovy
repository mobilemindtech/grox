package org.grox.data

import org.grox.data.validation.FieldError
import org.grox.data.validation.ValidationErrors

import java.util.function.Function

/** Representação JSON de entidades (rasas: associações viram {@code {id: x}}) e de erros. */
class DomainJson {

    static Map<String, Object> entity(Object e) {
        DomainClass d = Gorm.domain(e.getClass())
        Map<String, Object> out = d.composite ? [:] : [(d.identity.name): d.identity.get(e)]
        d.persistentProperties.values().each { DomainProperty p ->
            switch (p.kind) {
                case DomainProperty.Kind.BASIC -> out[p.name] = p.get(e)
                case DomainProperty.Kind.TENANT_ID -> {}
                case DomainProperty.Kind.MANY_TO_ONE, DomainProperty.Kind.ONE_TO_ONE -> {
                    Object ref = p.get(e)
                    out[p.name] = ref == null ? null : [id: Gorm.domain(ref.getClass()).identity.get(ref)]
                }
                case DomainProperty.Kind.ELEMENT_COLLECTION -> {
                    Collection c = (Collection) p.get(e)
                    out[p.name] = c == null ? [] : new ArrayList(c)
                }
                case DomainProperty.Kind.ONE_TO_MANY, DomainProperty.Kind.MANY_TO_MANY -> {
                    Collection c = (Collection) p.get(e)
                    out[p.name] = c == null ? [] : c.collect { [id: Gorm.domain(it.getClass()).identity.get(it)] }
                }
                default -> {}
            }
        }
        out
    }

    /**
     * Formato do Grails: {@code {"errors": [{"object", "field", "rejected-value", "message"}]}}.
     * @param messages texto de cada erro (o módulo web resolve pelas mensagens e o locale da
     *        requisição); null ou sem resultado usa a mensagem padrão do erro
     */
    static Map<String, Object> errors(ValidationErrors errors, Function<FieldError, String> messages = null) {
        [errors: errors.allErrors.collect { FieldError fe ->
            String message = messages?.apply(fe) ?: (fe.defaultMessage ?: fe.codes.last())
            Map m = [object: fe.objectName, field: fe.field, 'rejected-value': fe.rejectedValue, message: message]
            m.findAll { k, v -> k != 'field' || v != null }
        }]
    }
}
