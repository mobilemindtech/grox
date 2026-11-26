package org.grox.core

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.function.Function

/**
 * Conversão de texto (parâmetros, JSON) em tipos simples: números, booleanos, datas, enums. Usada por
 * {@code params.int('x')} e pelo binding de dados.
 */
class Conversions {

    static final Map<Class, Object> PRIMITIVE_DEFAULTS = [
            (int)    : 0, (long): 0L, (double): 0d, (float): 0f, (boolean): false,
            (short)  : (short) 0, (byte): (byte) 0, (char): (char) 0
    ]

    static final Map<Class, Closure> CONVERTERS = [
            (String)       : { String s -> s },
            (Integer)      : { String s -> Integer.valueOf(s) },
            (int)          : { String s -> Integer.valueOf(s) },
            (Long)         : { String s -> Long.valueOf(s) },
            (long)         : { String s -> Long.valueOf(s) },
            (Double)       : { String s -> Double.valueOf(s) },
            (double)       : { String s -> Double.valueOf(s) },
            (Float)        : { String s -> Float.valueOf(s) },
            (float)        : { String s -> Float.valueOf(s) },
            (Short)        : { String s -> Short.valueOf(s) },
            (short)        : { String s -> Short.valueOf(s) },
            (Byte)         : { String s -> Byte.valueOf(s) },
            (byte)         : { String s -> Byte.valueOf(s) },
            (Boolean)      : { String s -> s.toLowerCase() in ['true', 'on', 'yes', '1'] },
            (boolean)      : { String s -> s.toLowerCase() in ['true', 'on', 'yes', '1'] },
            (Character)    : { String s -> s.charAt(0) },
            (char)         : { String s -> s.charAt(0) },
            (BigDecimal)   : { String s -> new BigDecimal(s) },
            (BigInteger)   : { String s -> new BigInteger(s) },
            (UUID)         : { String s -> UUID.fromString(s) },
            (LocalDate)    : { String s -> LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s) },
            (LocalDateTime): { String s -> LocalDateTime.parse(s) },
            (LocalTime)    : { String s -> LocalTime.parse(s) },
            (Instant)      : { String s -> Instant.parse(s) },
            (OffsetDateTime): { String s -> OffsetDateTime.parse(s) },
            (ZonedDateTime): { String s -> ZonedDateTime.parse(s) },
            (Date)         : { String s -> parseDate(s) },
    ]

    /**
     * Editores de texto da aplicação por tipo (os {@code PropertyEditorRegistrar} do Spring, no módulo
     * Grails); null se não houver. Valem só no binding de dados da requisição.
     */
    static volatile Function<Class<?>, java.beans.PropertyEditor> editors

    @groovy.transform.CompileStatic
    static boolean isSimple(Class<?> type) {
        type.primitive || CONVERTERS.containsKey(type) || Number.isAssignableFrom(type) || type.enum
    }

    static Object primitiveDefault(Class<?> type) {
        PRIMITIVE_DEFAULTS[type]
    }

    /**
     * @param editors editores da aplicação (só no binding de dados; conversões internas do framework não os usam)
     */
    static Object convert(Object value, Class<?> type, Function<Class<?>, java.beans.PropertyEditor> editors = null) {
        if (value == null) return primitiveDefault(type)
        if (Collection.isAssignableFrom(type)) {
            Collection c = value instanceof Collection ? (Collection) value : [value]
            return Set.isAssignableFrom(type) ? new LinkedHashSet(c) : new ArrayList(c)
        }
        if (value instanceof Collection) value = ((Collection) value) ? ((Collection) value).iterator().next() : null
        if (value == null) return primitiveDefault(type)
        if (type.isInstance(value)) return value
        if (type.primitive && PRIMITIVE_DEFAULTS[type]?.getClass()?.isInstance(value)) return value
        String s = value.toString().trim()
        // vazio vira null/padrão antes dos editores, como no Grails (um CustomDateEditor sem allowEmpty lançaria exceção a cada campo vazio)
        if (s.empty && type != String) return primitiveDefault(type)
        // editores dos beans PropertyEditorRegistrar (ex.: formato de Date da aplicação)
        java.beans.PropertyEditor editor = editors != null && type != String ? editors.apply(type) : null
        if (editor != null) {
            try {
                editor.setAsText(s)
                return editor.value
            } catch (IllegalArgumentException ignored) {
                // texto que o editor da aplicação não aceita: segue para a conversão padrão
            }
        }
        try {
            Closure conv = CONVERTERS[type]
            if (conv != null) return conv.call(type == String ? value.toString() : s)
            if (type.enum) return Enum.valueOf((Class<Enum>) type, s)
        } catch (Exception ignored) {
            return primitiveDefault(type)
        }
        value
    }

    static final List<String> DEFAULT_DATE_FORMATS = ["yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX",
                                                      "yyyy-MM-dd'T'HH:mm:ss", 'yyyy-MM-dd HH:mm:ss', 'yyyy-MM-dd']

    /** Formatos de {@code Date} da aplicação ({@code grails.databinding.dateFormats}), tentados antes dos padrões. */
    static volatile List<String> dateFormats = []

    static Date parseDate(String s) {
        for (String f : (dateFormats ? dateFormats + DEFAULT_DATE_FORMATS : DEFAULT_DATE_FORMATS)) {
            try {
                def fmt = new SimpleDateFormat(f)
                fmt.lenient = false
                return fmt.parse(s)
            } catch (java.text.ParseException ignored) {
            }
        }
        throw new IllegalArgumentException("Data inválida: $s")
    }
}
