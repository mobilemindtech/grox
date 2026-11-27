package org.grox.data.validation

import org.grox.core.ClassUtils

import java.util.regex.Pattern

/**
 * Aplica as constraints do Grails. Códigos de erro e argumentos seguem o Grails:
 * {@code {0}} propriedade, {@code {1}} classe, {@code {2}} valor, {@code {3}...} parâmetros.
 */
class Validator {

    static final Pattern EMAIL = ~/^[^@\s]+@[^@\s]+\.[^@\s]+$/

    /** Constraint → [código Grails, código da mensagem padrão]. */
    static final Map<String, List<String>> CODES = [
            nullable : ['nullable', 'default.null.message'],
            blank    : ['blank', 'default.blank.message'],
            sizeSmall: ['size.toosmall', 'default.invalid.size.message'],
            sizeBig  : ['size.toobig', 'default.invalid.size.message'],
            minSize  : ['minSize.notmet', 'default.invalid.min.size.message'],
            maxSize  : ['maxSize.exceeded', 'default.invalid.max.size.message'],
            min      : ['min.notmet', 'default.invalid.min.message'],
            max      : ['max.exceeded', 'default.invalid.max.message'],
            rangeLow : ['range.toosmall', 'default.invalid.range.message'],
            rangeHigh: ['range.toobig', 'default.invalid.range.message'],
            inList   : ['not.inList', 'default.not.inlist.message'],
            matches  : ['matches.invalid', 'default.doesnt.match.message'],
            email    : ['email.invalid', 'default.invalid.email.message'],
            url      : ['url.invalid', 'default.invalid.url.message'],
            unique   : ['unique', 'default.not.unique.message'],
            validator: ['validator.invalid', 'default.invalid.validator.message'],
    ]

    /** Verificação de unicidade (fornecida pela camada de dados). */
    static interface UniqueChecker {
        boolean isUnique(Object target, String property, Object value, List<String> scope)
    }

    static volatile UniqueChecker uniqueChecker

    /**
     * @param constraints propriedade → regras (já com os padrões, ex. nullable false)
     * @param fields se informado, valida só essas propriedades
     */
    static void validate(Object target, Map<String, Map<String, Object>> constraints, ValidationErrors errors, List<String> fields = null) {
        Class<?> type = ClassUtils.userClass(target.getClass())
        constraints.each { String prop, Map<String, Object> rules ->
            int dot = prop.indexOf('.')
            if (fields != null && !(prop in fields) && !(dot > 0 && prop.substring(0, dot) in fields)) return
            Object value = dot < 0 ? (target.hasProperty(prop) ? target[prop] : null) : valueAt(target, prop)
            new Validator(target: target, type: type, prop: prop, value: value, errors: errors).check(rules)
        }
    }

    Object target
    Class<?> type
    String prop
    Object value
    ValidationErrors errors

    private void check(Map<String, Object> rules) {
        if (value == null) {
            if (rules.nullable != true) reject('nullable')
            return
        }
        if (value instanceof CharSequence && value.toString().trim().isEmpty() && rules.blank == false) {
            reject('blank')
            return
        }
        Integer length = lengthOf(value)
        if (rules.size instanceof Range && length != null) {
            Range r = (Range) rules.size
            if (length < (r.from as int)) reject('sizeSmall', r.from, r.to)
            else if (length > (r.to as int)) reject('sizeBig', r.from, r.to)
        }
        if (rules.minSize != null && length != null && length < (rules.minSize as int)) reject('minSize', rules.minSize)
        if (rules.maxSize != null && length != null && length > (rules.maxSize as int)) reject('maxSize', rules.maxSize)
        if (rules.min != null && value instanceof Comparable && ((Comparable) value) < rules.min) reject('min', rules.min)
        if (rules.max != null && value instanceof Comparable && ((Comparable) value) > rules.max) reject('max', rules.max)
        if (rules.range instanceof Range && value instanceof Comparable) {
            Range r = (Range) rules.range
            if (((Comparable) value) < r.from) reject('rangeLow', r.from, r.to)
            else if (((Comparable) value) > r.to) reject('rangeHigh', r.from, r.to)
        }
        if (rules.inList instanceof Collection && !((Collection) rules.inList).contains(value)) reject('inList', rules.inList)
        if (rules.matches != null && !(value.toString() ==~ rules.matches.toString())) reject('matches', rules.matches)
        if (rules.email == true && !(value.toString() ==~ EMAIL)) reject('email')
        if (rules.url == true && !validUrl(value.toString())) reject('url')
        if (rules.unique && uniqueChecker != null) {
            List<String> scope = rules.unique instanceof CharSequence ? [rules.unique.toString()] :
                    rules.unique instanceof Collection ? ((Collection) rules.unique)*.toString() : []
            if (!uniqueChecker.isUnique(target, prop, value, scope)) reject('unique')
        }
        if (rules.validator instanceof Closure) customValidator((Closure) rules.validator)
    }

    private void customValidator(Closure v) {
        Object result = switch (v.maximumNumberOfParameters) {
            case 0, 1 -> v.call(value)
            case 2 -> v.call(value, target)
            default -> v.call(value, target, errors)
        }
        if (result == null || result == true) return
        if (result == false) {
            reject('validator')
        } else if (result instanceof CharSequence) {
            rejectCustom(result.toString(), [])
        } else if (result instanceof List && result) {
            List l = (List) result
            rejectCustom(l[0].toString(), l.drop(1))
        }
    }

    private void rejectCustom(String code, List extraArgs) {
        String prefix = ClassUtils.propertyName(type) + '.' + prop
        errors.add(new FieldError(objectName: type.name, field: prop, rejectedValue: value,
                codes: ["${type.name}.${prop}.${code}".toString(), "${prefix}.${code}".toString(), code, 'default.invalid.validator.message'],
                arguments: ([prop, type.simpleName, value] + extraArgs) as Object[]))
    }

    private void reject(String key, Object... params) {
        List<String> c = CODES[key]
        String code = c[0]
        errors.add(new FieldError(objectName: type.name, field: prop, rejectedValue: value,
                codes: ["${type.name}.${prop}.${code}".toString(), "${ClassUtils.propertyName(type)}.${prop}.${code}".toString(), c[1]],
                arguments: ([prop, type.simpleName, value] + (params as List)) as Object[]))
    }

    /** {@code endereco.rua}: propriedade de um componente ({@code embedded}); null se o componente for null. */
    private static Object valueAt(Object target, String path) {
        Object current = target
        for (String part : path.tokenize('.')) {
            if (current == null || !current.hasProperty(part)) return null
            current = current[part]
        }
        current
    }

    private static Integer lengthOf(Object v) {
        if (v instanceof CharSequence) return v.toString().length()
        if (v instanceof Collection) return ((Collection) v).size()
        if (v instanceof Map) return ((Map) v).size()
        if (v.getClass().array) return java.lang.reflect.Array.getLength(v)
        null
    }

    private static boolean validUrl(String s) {
        try {
            URI u = new URI(s)
            return u.scheme in ['http', 'https', 'ftp'] && u.host
        } catch (URISyntaxException ignored) {
            return false
        }
    }
}
