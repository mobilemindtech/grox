package org.grox.web.scaffolding

import org.grox.core.ClassUtils
import org.grox.data.DomainClass
import org.grox.data.DomainProperty
import org.grox.data.Gorm
import org.grox.http.Html
import org.grox.view.gsp.GspRenderContext
import org.grox.view.taglib.TagLibApi

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.Temporal

/**
 * Subconjunto das tags {@code f:} do plugin fields do Grails, usado pelas views do scaffold: {@code f:all},
 * {@code f:field}, {@code f:display} e {@code f:table}. O widget sai do tipo e das constraints da propriedade
 * ({@code inList}, {@code email}, {@code url}, {@code password}, {@code maxSize}, {@code widget}); não há os
 * templates por propriedade ({@code views/_fields/}) do plugin.
 */
class FieldsTagLib implements TagLibApi {

    static namespace = 'f'

    /** Fora dos formulários e das tabelas, como no plugin. */
    static final Set<String> EXCLUDED = ['id', 'version', 'dateCreated', 'lastUpdated'] as Set
    static final Set<DomainProperty.Kind> COLLECTIONS = [DomainProperty.Kind.ONE_TO_MANY, DomainProperty.Kind.MANY_TO_MANY,
                                                         DomainProperty.Kind.ELEMENT_COLLECTION] as Set
    /** Colunas de {@code f:table} sem {@code properties}. */
    static final int TABLE_COLUMNS = 7

    /** {@code <f:all bean="livro" except="isbn"/>}: um campo por propriedade editável, na ordem das constraints. */
    def all = { Map attrs ->
        Object bean = resolveBean(attrs.bean)
        if (bean == null) throw new IllegalArgumentException('<f:all> exige o atributo bean')
        List<String> except = names(attrs.except)
        String prefix = attrs.prefix?.toString() ?: ''
        for (DomainProperty p : editable(domainOf(bean.getClass()))) {
            if (!(p.name in except)) out << fieldHtml(bean, p, prefix)
        }
    }

    /** {@code <f:field bean="livro" property="titulo"/>}: rótulo, widget e marcação de erro e obrigatório. */
    def field = { Map attrs ->
        Object bean = resolveBean(attrs.bean)
        String name = attrs.property?.toString()
        if (bean == null || !name) throw new IllegalArgumentException('<f:field> exige bean e property')
        DomainProperty p = domainOf(bean.getClass()).persistentProperties[name]
        if (p == null) throw new IllegalArgumentException("<f:field>: ${bean.getClass().simpleName} não tem a propriedade ${name}")
        out << fieldHtml(bean, p, attrs.prefix?.toString() ?: '')
    }

    /** {@code <f:display bean="livro"/>}: lista rótulo/valor; com {@code property}, só o valor. */
    def display = { Map attrs ->
        Object bean = resolveBean(attrs.bean)
        if (bean == null) return
        DomainClass dc = domainOf(bean.getClass())
        if (attrs.property) {
            out << displayValue(dc.persistentProperties[attrs.property.toString()], bean)
            return
        }
        List<String> except = names(attrs.except)
        out << '<ol class="property-list ' << dc.propertyName << '">'
        for (DomainProperty p : displayed(dc)) {
            if (p.name in except) continue
            String id = "${p.name}-label"
            out << '<li class="fieldcontain"><span id="' << id << '" class="property-label">' << Html.escape(label(dc, p)) <<
                    '</span><div class="property-value" aria-labelledby="' << id << '">' << displayValue(p, bean) << '</div></li>'
        }
        out << '</ol>'
    }

    /**
     * {@code <f:table collection="${livroList}" properties="['titulo', 'autor']"/>}: a primeira coluna leva ao
     * {@code show}. Sem {@code properties}, as {@value #TABLE_COLUMNS} primeiras; {@code domainClass} vale para
     * a lista vazia.
     */
    def table = { Map attrs ->
        Collection items = (Collection) (attrs.collection ?: [])
        Class<?> type = attrs.domainClass instanceof Class ? (Class) attrs.domainClass :
                attrs.domainClass ? Class.forName(attrs.domainClass.toString(), true, Thread.currentThread().contextClassLoader) :
                        items ? items.iterator().next().getClass() : null
        if (type == null) return
        DomainClass dc = domainOf(type)
        List<DomainProperty> cols = attrs.properties ? names(attrs.properties).collect { dc.persistentProperties[it] }.findAll() :
                displayed(dc).findAll { !(it.kind in COLLECTIONS) }.take(TABLE_COLUMNS)
        out << '<table><thead><tr>'
        cols.each { DomainProperty p -> out << '<th>' << Html.escape(label(dc, p)) << '</th>' }
        out << '</tr></thead><tbody>'
        items.eachWithIndex { Object bean, int i ->
            out << '<tr class="' << (i % 2 == 0 ? 'even' : 'odd') << '">'
            cols.eachWithIndex { DomainProperty p, int c ->
                out << '<td>'
                if (c == 0) {
                    out << g.link([action: 'show', id: bean.id], displayValue(p, bean, false))
                } else {
                    out << displayValue(p, bean)
                }
                out << '</td>'
            }
            out << '</tr>'
        }
        out << '</tbody></table>'
    }

    // --- campos ---

    private String fieldHtml(Object bean, DomainProperty p, String prefix) {
        DomainClass dc = domainOf(bean.getClass())
        if (p.kind == DomainProperty.Kind.EMBEDDED) return embeddedHtml(bean, dc, p, prefix)
        Map<String, Object> rules = constraintsOf(dc, p.name)
        boolean required = !(rules.nullable as boolean) && !(p.type in [boolean, Boolean])
        boolean invalid = bean.respondsTo('hasErrors') && bean.errors?.getFieldError(p.name) != null
        String name = prefix + p.name
        StringBuilder sb = new StringBuilder()
        sb << '<div class="fieldcontain' << (required ? ' required' : '') << (invalid ? ' error' : '') << '">'
        sb << '<label for="' << name << '">' << Html.escape(label(dc, p))
        if (required) sb << '<span class="required-indicator">*</span>'
        sb << '</label>' << widget(bean, p, name, rules, required) << '</div>'
        sb.toString()
    }

    /**
     * Componente ({@code embedded}): um {@code fieldset} com um campo por propriedade ({@code endereco.rua}), como no
     * plugin fields do Grails; o binding de {@code params.endereco} preenche o componente. Rótulo
     * {@code pessoa.endereco.rua.label}.
     */
    private String embeddedHtml(Object bean, DomainClass dc, DomainProperty p, String prefix) {
        Object comp = bean."${p.name}" ?: p.referencedType.getDeclaredConstructor().newInstance()
        Map<String, Map<String, Object>> rules = org.grox.data.validation.ConstraintsBuilder.declared(p.referencedType)
        StringBuilder sb = new StringBuilder('<fieldset class="embedded ' + p.name + '"><legend>' + Html.escape(label(dc, p)) + '</legend>')
        p.embeddedProperties.values().each { DomainProperty c ->
            String name = prefix + p.name + '.' + c.name
            String text = g.message(code: "${dc.propertyName}.${p.name}.${c.name}.label", default: naturalName(c.name)).toString()
            sb << '<div class="fieldcontain"><label for="' << name << '">' << Html.escape(text) << '</label>' <<
                    widget(comp, c, name, [nullable: true] + (rules[c.name] ?: [:]), false) << '</div>'
        }
        sb << '</fieldset>'
        sb.toString()
    }

    private String widget(Object bean, DomainProperty p, String name, Map<String, Object> rules, boolean required) {
        Object value = valueOf(bean, p)
        Class<?> t = p.type
        Map<String, Object> base = [name: name, id: name]
        if (required && !(t in [boolean, Boolean])) base.required = 'required'
        if (p.kind in [DomainProperty.Kind.MANY_TO_ONE, DomainProperty.Kind.ONE_TO_ONE]) {
            Map<String, Object> a = base + [from: Gorm.api(p.referencedType).list(), optionKey: 'id', value: value?.id]
            if (!required) a.noSelection = ['null': '']
            return g.select(a).toString()
        }
        if (rules.inList) {
            Map<String, Object> a = base + [from: rules.inList, value: value]
            if (!required) a.noSelection = ['': '']
            return g.select(a).toString()
        }
        if (t.enum) {
            Map<String, Object> a = base + [from: t.enumConstants, optionKey: 'name', value: (value as Enum)?.name()]
            if (!required) a.noSelection = ['': '']
            return g.select(a).toString()
        }
        if (t in [boolean, Boolean]) return g.checkBox(name: name, id: name, value: value as boolean).toString()
        if (t == LocalDate) return input('date', base, value?.toString())
        if (Date.isAssignableFrom(t) || t == LocalDateTime) {
            return g.datePicker(name: name, value: value, precision: rules.precision ?: 'day', noSelection: required ? null : ['': '']).toString()
        }
        if (Number.isAssignableFrom(t) || (t.primitive && t != char)) {
            Map<String, Object> a = new LinkedHashMap(base)
            if (t in [BigDecimal, Double, Float, double, float]) a.step = 'any'
            if (rules.min != null) a.min = rules.min
            if (rules.max != null) a.max = rules.max
            return input('number', a, value?.toString())
        }
        String w = rules.widget?.toString()
        Integer maxSize = (rules.maxSize ?: (rules.size instanceof Range ? ((Range) rules.size).to : null)) as Integer
        if (maxSize) base.maxlength = maxSize
        if (w == 'textarea' || (maxSize != null && maxSize > 255)) {
            return '<textarea' + attributes(base) + '>' + Html.escape(value ?: '') + '</textarea>'
        }
        String type = rules.email ? 'email' : rules.url ? 'url' : (rules.password || w == 'password' || p.name ==~ /(?i).*password.*/) ? 'password' : 'text'
        input(type, base, type == 'password' ? null : value?.toString())
    }

    private static String input(String type, Map<String, Object> attrs, String value) {
        '<input type="' + type + '"' + attributes(attrs) + (value != null ? ' value="' + Html.escape(value) + '"' : '') + '/>'
    }

    private static String attributes(Map<String, Object> attrs) {
        attrs.findAll { k, v -> v != null }.collect { k, v -> ' ' + k + '="' + Html.escape(v.toString()) + '"' }.join('')
    }

    /** O valor rejeitado (se houve erro de binding) ou o atual. */
    private static Object valueOf(Object bean, DomainProperty p) {
        Object fe = bean.respondsTo('hasErrors') ? bean.errors?.getFieldError(p.name) : null
        fe != null && fe.rejectedValue != null && !(p.kind in [DomainProperty.Kind.MANY_TO_ONE, DomainProperty.Kind.ONE_TO_ONE]) ?
                fe.rejectedValue : bean."${p.name}"
    }

    // --- exibição ---

    private String displayValue(DomainProperty p, Object bean, boolean link = true) {
        if (p == null) return ''
        Object v = bean."${p.name}"
        if (v == null) return ''
        if (p.kind in [DomainProperty.Kind.MANY_TO_ONE, DomainProperty.Kind.ONE_TO_ONE] && link) {
            return g.link([controller: ClassUtils.propertyName(p.referencedType), action: 'show', id: v.id], Html.escape(v.toString())).toString()
        }
        if (v instanceof Collection) return Html.escape(((Collection) v)*.toString().join(', '))
        if (p.kind == DomainProperty.Kind.EMBEDDED) {
            // componente sem toString próprio: os valores preenchidos, na ordem das propriedades
            return Html.escape(p.embeddedProperties.keySet().collect { v[it] }.findAll { it != null }*.toString().join(', '))
        }
        if (v instanceof Boolean) return Html.escape(g.message(code: "boolean.${v}", default: v.toString()).toString())
        if (v instanceof Date) return g.formatDate(date: v).toString()
        if (v instanceof Temporal) return Html.escape(v.toString().replace('T', ' '))
        Html.escape(v.toString())
    }

    // --- metadados ---

    /** Propriedades persistentes, na ordem das constraints e depois na da declaração. */
    private static List<DomainProperty> displayed(DomainClass dc) {
        List<String> order = (dc.constraints?.keySet() ?: []) as List<String>
        List<DomainProperty> props = dc.persistentProperties.values().findAll { DomainProperty p ->
            !(p.kind in [DomainProperty.Kind.ID, DomainProperty.Kind.VERSION, DomainProperty.Kind.TENANT_ID]) &&
                    !(p.name in EXCLUDED) && !p.formula
        }
        props.sort(false) { DomainProperty p -> int i = order.indexOf(p.name); i < 0 ? order.size() + props.indexOf(p) : i }
    }

    /** As de {@link #displayed} sem as coleções ({@code hasMany}): o formulário não as edita. */
    private static List<DomainProperty> editable(DomainClass dc) {
        displayed(dc).findAll { DomainProperty p -> !(p.kind in COLLECTIONS) && constraintsOf(dc, p.name).display != false }
    }

    private static Map<String, Object> constraintsOf(DomainClass dc, String name) {
        dc.effectiveConstraints()[name] ?: [:]
    }

    private String label(DomainClass dc, DomainProperty p) {
        g.message(code: "${dc.propertyName}.${p.name}.label", default: naturalName(p.name)).toString()
    }

    /** {@code dataNascimento} → {@code Data Nascimento}, como o {@code GrailsNameUtils.getNaturalName}. */
    static String naturalName(String name) {
        name.replaceAll(/([a-z0-9])([A-Z])/, '$1 $2').capitalize()
    }

    private static DomainClass domainOf(Class<?> type) {
        Gorm.domain(type)
    }

    /** {@code bean="livro"} (variável da página) ou {@code bean="${livro}"}. */
    private static Object resolveBean(Object bean) {
        bean instanceof CharSequence ? GspRenderContext.current().currentBinding?.getVariable(bean.toString()) : bean
    }

    private static List<String> names(Object v) {
        if (v == null) return []
        if (v instanceof Collection) return ((Collection) v)*.toString()
        String s = v.toString().trim()
        if (s.startsWith('[')) s = s.substring(1, s.length() - 1)
        s.split(',')*.trim()*.replaceAll(/^['"]|['"]$/, '').findAll()
    }
}
