package org.grox.data.binding

import org.grox.core.ClassUtils
import org.grox.core.Conversions
import org.grox.data.Gorm
import org.springframework.web.multipart.MultipartFile
import org.grox.data.validation.FieldError
import org.grox.data.validation.ConstraintsBuilder
import org.grox.data.validation.ValidationSupport

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Conversão de tipos e binding de mapas (params/JSON) em objetos, estilo Grails. Os editores da
 * aplicação ({@code PropertyEditorRegistrar}) entram pelo gancho {@link Conversions#editors}, que o
 * módulo Grails preenche.
 */
class DataBinder {

    static final Map<Class, Object> PRIMITIVE_DEFAULTS = Conversions.PRIMITIVE_DEFAULTS
    static final Map<Class, Closure> CONVERTERS = Conversions.CONVERTERS

    static boolean isSimple(Class<?> type) {
        Conversions.isSimple(type)
    }

    static Object primitiveDefault(Class<?> type) {
        Conversions.primitiveDefault(type)
    }

    /** Conversão do binding de dados da requisição: considera os editores dos beans PropertyEditorRegistrar. */
    static Object bindValue(Object value, Class<?> type) {
        convert(value, type, true)
    }

    /** @param editors só no binding de dados ({@link #bindValue}); conversões internas do framework não os usam */
    static Object convert(Object value, Class<?> type, boolean editors = false) {
        Conversions.convert(value, type, editors ? Conversions.editors : null)
    }

    /** {@code grupo.id=3} / {@code {"grupo": {"id": 3}}} / {@code grupo=3} → {@code Grupo.get(3)}. */
    static Object resolveAssociation(Class<?> type, Object raw) {
        if (Gorm.isDomain(raw?.getClass())) return raw
        Object id = raw instanceof Map ? ((Map) raw).get('id') : raw
        if (id instanceof Collection) id = ((Collection) id) ? ((Collection) id).iterator().next() : null
        if (id == null || id.toString().trim().isEmpty() || id.toString() == 'null') return null
        Gorm.api(type).get(id)
    }

    static final List<String> DEFAULT_DATE_FORMATS = Conversions.DEFAULT_DATE_FORMATS

    /** {@code grails.databinding.dateFormats}: tentados na ordem, antes dos formatos padrão (definido na partida). */
    static List<String> getDateFormats() { Conversions.dateFormats }

    static void setDateFormats(List<String> formats) { Conversions.dateFormats = formats }

    static Date parseDate(String s) {
        Conversions.parseDate(s)
    }

    static final Set<String> NEVER_BIND = ['class', 'metaClass', 'properties'] as Set

    /**
     * {@code bindData(target, params, [include: [...], exclude: [...]])}.
     * Mapas aninhados (JSON) ou chaves com ponto ({@code endereco.cidade}) populam
     * propriedades de objetos.
     */
    /** Nunca vinculados em entidades, a menos que listados em {@code include}. */
    static final Set<String> ENTITY_PROTECTED = ['id', 'version', 'dateCreated', 'lastUpdated', 'errors', 'attached'] as Set

    static Object bind(Object target, Object source, Map options = [:]) {
        if (source == null) return target
        Map src = source instanceof Map ? (Map) source : source.properties
        List include = options?.include as List
        List exclude = (options?.exclude ?: []) as List
        boolean entity = Gorm.isDomain(target.getClass())
        String tenantProp = entity ? Gorm.domain(target.getClass()).tenantProperty?.name : null
        // constraint bindable: false (domain classes e command objects), como no Grails; include explícito vence
        Map<String, Map<String, Object>> rules = ConstraintsBuilder.declared(ClassUtils.userClass(target.getClass()))
        for (MetaProperty mp : target.metaClass.properties) {
            String n = mp.name
            if (n in NEVER_BIND || n in exclude || (include && !(n in include))) continue
            if (entity && n in ENTITY_PROTECTED && !(include && n in include)) continue
            if (n == tenantProp) continue
            if (rules[n]?.bindable == false && !(include && n in include)) continue
            if (!(mp instanceof MetaBeanProperty) || ((MetaBeanProperty) mp).setter == null) continue
            Object raw = src.get(n)
            if ((raw == 'date.struct' || (raw == null && src.containsKey(n + '_year'))) && (mp.type == Date || mp.type == LocalDate || mp.type == LocalDateTime)) {
                // <g:datePicker>: nome_year, nome_month, nome_day [, nome_hour, nome_minute]
                mp.setProperty(target, dateStruct(src, n, mp.type))
                continue
            }
            if (raw == null && !src.containsKey(n)) {
                // <g:checkBox> desmarcado envia apenas "_campo"
                if (src.containsKey('_' + n) && (mp.type == Boolean || mp.type == boolean)) mp.setProperty(target, false)
                continue
            }
            Class<?> t = mp.type
            if (raw instanceof MultipartFile) {
                MultipartFile f = (MultipartFile) raw
                if (t == byte[]) {
                    if (!f.empty) mp.setProperty(target, f.bytes)
                } else if (t.isInstance(f)) {
                    mp.setProperty(target, f)
                }
                continue
            }
            if (Gorm.isDomain(t)) {
                mp.setProperty(target, resolveAssociation(t, raw))
                continue
            }
            if (t == String && raw instanceof CharSequence) {
                // como no Grails: trim e string vazia vira null
                String s = raw.toString().trim()
                mp.setProperty(target, s.isEmpty() ? null : s)
                continue
            }
            String format = bindingFormat(target.getClass(), n)
            if (format && raw != null && !(raw instanceof Map)) {
                Object v = raw instanceof Collection ? (((Collection) raw) ? ((Collection) raw).iterator().next() : null) : raw
                if (v == null || v.toString().trim().isEmpty()) { if (!t.primitive) mp.setProperty(target, null); continue }
                if (t.isInstance(v)) { mp.setProperty(target, v); continue }
                try {
                    mp.setProperty(target, formatted(v.toString().trim(), format, t))
                } catch (Exception ignored) {
                    if (!t.primitive) mp.setProperty(target, null)
                    recordMismatch(target, n, t, raw)
                }
                continue
            }
            if (raw instanceof Map && !Map.isAssignableFrom(t) && !isSimple(t) && t != Object) {
                Object nested = mp.getProperty(target) ?: t.getDeclaredConstructor().newInstance()
                bind(nested, raw)
                mp.setProperty(target, nested)
            } else if (raw == null && t.primitive) {
                continue
            } else if (t != Object && isSimple(t) && mismatch(raw, t)) {
                // como no Grails: valor não conversível vira erro typeMismatch (e a propriedade fica null)
                if (!t.primitive) mp.setProperty(target, null)
                recordMismatch(target, n, t, raw)
            } else {
                mp.setProperty(target, t == Object ? raw : bindValue(raw, t))
            }
        }
        target
    }

    static Object dateStruct(Map src, String name, Class<?> type) {
        Closure<Integer> part = { String p, int dflt ->
            Object v = src.get("${name}_${p}".toString())
            if (v instanceof Collection) v = ((Collection) v) ? ((Collection) v).iterator().next() : null
            v == null || v.toString().trim().isEmpty() ? dflt : Integer.parseInt(v.toString().trim())
        }
        Integer year = part('year', -1)
        if (year < 0) return null
        int month = part('month', 1), day = part('day', 1), hour = part('hour', 0), minute = part('minute', 0)
        if (type == LocalDate) return LocalDate.of(year, month, day)
        if (type == LocalDateTime) return LocalDateTime.of(year, month, day, hour, minute)
        Calendar c = Calendar.instance
        c.clear()
        c.set(year, month - 1, day, hour, minute, 0)
        c.time
    }

    private static final Map<List, String> FORMATS = new java.util.concurrent.ConcurrentHashMap<>()

    /** Valor de {@code @BindingFormat} do campo (procurando nas superclasses), ou vazio. */
    static String bindingFormat(Class<?> type, String property) {
        FORMATS.computeIfAbsent([type, property]) {
            for (Class<?> k = type; k != null && k != Object; k = k.superclass) {
                java.lang.reflect.Field f = k.declaredFields.find { it.name == property }
                if (f != null) return f.getAnnotation(grails.databinding.BindingFormat)?.value() ?: ''
            }
            ''
        }
    }

    static Object formatted(String s, String format, Class<?> type) {
        if (type == Date) {
            def fmt = new SimpleDateFormat(format)
            fmt.lenient = false
            return fmt.parse(s)
        }
        def f = java.time.format.DateTimeFormatter.ofPattern(format)
        if (type == LocalDate) return LocalDate.parse(s, f)
        if (type == LocalDateTime) return LocalDateTime.parse(s, f)
        if (type == LocalTime) return LocalTime.parse(s, f)
        if (Number.isAssignableFrom(type) || type.primitive) {
            Number num = new java.text.DecimalFormat(format).parse(s)
            return convert(num.toString(), type)
        }
        convert(s, type)
    }

    /** true se {@code raw} tem conteúdo mas não converte para {@code type}. */
    static boolean mismatch(Object raw, Class<?> type) {
        Object v = raw instanceof Collection ? (((Collection) raw) ? ((Collection) raw).iterator().next() : null) : raw
        if (v == null || type.isInstance(v) || Collection.isAssignableFrom(type)) return false
        String s = v.toString().trim()
        if (s.isEmpty()) return false
        Closure conv = CONVERTERS[type]
        try {
            if (conv != null) conv.call(type == String ? v.toString() : s)
            else if (type.enum) Enum.valueOf((Class<Enum>) type, s)
            return false
        } catch (Exception ignored) {
            return true
        }
    }

    static final Map<Class, Class> WRAPPERS = [(int): Integer, (long): Long, (double): Double, (float): Float,
                                               (boolean): Boolean, (short): Short, (byte): Byte, (char): Character]

    private static void recordMismatch(Object target, String prop, Class<?> type, Object raw) {
        if (!(target instanceof ValidationSupport)) return
        Class<?> owner = ClassUtils.userClass(target.getClass())
        String typeName = (WRAPPERS[type] ?: type).name
        ((ValidationSupport) target).addBindingError(new FieldError(objectName: owner.name, field: prop, rejectedValue: raw,
                codes: ["typeMismatch.${owner.name}.${prop}".toString(), "typeMismatch.${ClassUtils.propertyName(owner)}.${prop}".toString(),
                        "typeMismatch.${prop}".toString(), "typeMismatch.${typeName}".toString(), 'typeMismatch'],
                arguments: [prop, owner.simpleName, raw] as Object[]))
    }
}
