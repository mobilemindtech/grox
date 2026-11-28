package org.grox.http

import org.grox.core.Conversions

/**
 * Equivalente ao {@code GrailsParameterMap}: valores String ou List, conversão
 * tipada ({@code params.int('max')}) e acesso aninhado ({@code params.book.title}
 * para a chave {@code book.title}).
 */
class GroxParams extends grails.web.servlet.mvc.GrailsParameterMap {

    @Override
    @groovy.transform.CompileStatic
    Object get(Object key) {
        if (containsKey(key)) return super.get(key)
        String prefix = String.valueOf(key) + '.'
        GroxParams nested = null
        for (Map.Entry<String, Object> e : entrySet()) {
            if (e.key.startsWith(prefix)) {
                if (nested == null) nested = new GroxParams()
                nested.put(e.key.substring(prefix.length()), e.value)
            }
        }
        nested
    }

    private Object first(String name) {
        Object v = get(name)
        v instanceof List ? (((List) v) ? ((List) v)[0] : null) : v
    }

    private <T> T typed(String name, Class<T> type, T defaultValue) {
        Object v = first(name)
        T converted = v == null ? null : (T) Conversions.convert(v, type, Conversions.editors)
        converted != null ? converted : defaultValue
    }

    Integer 'int'(String name, Integer defaultValue = null) { typed(name, Integer, defaultValue) }

    Long 'long'(String name, Long defaultValue = null) { typed(name, Long, defaultValue) }

    Double 'double'(String name, Double defaultValue = null) { typed(name, Double, defaultValue) }

    Float 'float'(String name, Float defaultValue = null) { typed(name, Float, defaultValue) }

    Short 'short'(String name, Short defaultValue = null) { typed(name, Short, defaultValue) }

    Byte 'byte'(String name, Byte defaultValue = null) { typed(name, Byte, defaultValue) }

    BigDecimal bigDecimal(String name, BigDecimal defaultValue = null) { typed(name, BigDecimal, defaultValue) }

    Boolean 'boolean'(String name, Boolean defaultValue = null) {
        Object v = first(name)
        if (v == null) return defaultValue
        String s = v.toString().trim().toLowerCase()
        if (s in ['true', 'on', 'yes', '1']) return true
        if (s in ['false', 'off', 'no', '0']) return false
        defaultValue
    }

    List list(String name) {
        Object v = get(name)
        v == null ? [] : (v instanceof Collection ? new ArrayList((Collection) v) : [v])
    }

    Date date(String name, String format = null) {
        Object v = first(name)
        if (v == null) return null
        if (format) {
            try {
                return new java.text.SimpleDateFormat(format).parse(v.toString())
            } catch (java.text.ParseException ignored) {
                return null
            }
        }
        (Date) Conversions.convert(v, Date, Conversions.editors)
    }
}
