package org.grox.view.taglib

import org.grox.http.GroxRequest
import org.grox.http.Html

import java.util.function.Function

/** Tags {@code g:} de formulário. */
class FormTagLib implements TagLibApi {

    /**
     * Campos ocultos acrescentados aos forms que não são GET (nome → valor), como o token CSRF; o módulo
     * que conhece a segurança define. Null: nenhum.
     */
    static volatile Function<GroxRequest, Map<String, String>> hiddenFields

    /**
     * {@code <g:form action="save" method="PUT">}: métodos além de GET/POST viram
     * POST com o campo {@code _method} (interpretado pelo dispatcher).
     */
    @groovy.transform.CompileStatic
    void form(Map attrs, Closure body) {
        String method = (attrs.remove('method') ?: 'POST').toString().toUpperCase()
        Map<String, Object> link = ApplicationTagLib.extractLinkAttrs(attrs)
        Object url = link.get('url')
        String action = url instanceof CharSequence ? url.toString() :
                (link ? webRequest.links.link(link, controllerName) : request.path)
        Writer w = out
        w.write('<form action="')
        w.write(Html.escape(action))
        w.write(method == 'GET' ? '" method="get"' : '" method="post"')
        w.write(Html.attributes(attrs))
        w.write('>')
        if (method != 'GET' && method != 'POST') w.write('<input type="hidden" name="_method" value="' + method + '"/>')
        Function<GroxRequest, Map<String, String>> fields = hiddenFields
        if (method != 'GET' && fields != null) {
            Map<String, String> hidden = fields.apply(request)
            if (hidden) {
                for (Map.Entry<String, String> e : hidden.entrySet()) {
                    w.write('<input type="hidden" name="' + Html.escape(e.key) + '" value="' + Html.escape(e.value) + '"/>')
                }
            }
        }
        ApplicationTagLib.writeBody(w, body)
        w.write('</form>')
    }

    /** {@code <g:uploadForm action="upload">}: form com {@code enctype="multipart/form-data"}. */
    def uploadForm = { Map attrs, body ->
        attrs.enctype = 'multipart/form-data'
        form(attrs, body)  // form já escreve em out (não usar "out << form(...)")
    }

    def field = { Map attrs -> input(attrs.remove('type')?.toString() ?: 'text', attrs) }

    def textField = { Map attrs -> input('text', attrs) }

    def passwordField = { Map attrs -> input('password', attrs) }

    def hiddenField = { Map attrs -> input('hidden', attrs) }

    def emailField = { Map attrs -> input('email', attrs) }

    def numberField = { Map attrs -> input('number', attrs) }

    def dateField = { Map attrs -> input('date', attrs) }

    def submitButton = { Map attrs ->
        attrs.value = attrs.value ?: attrs.name ?: 'Submit'
        out << '<input type="submit"' << Html.attributes(attrs) << ' />'
    }

    /** {@code <g:actionSubmit action="delete" value="Excluir"/>}: submit que escolhe a action ({@code _action_delete}). */
    def actionSubmit = { Map attrs ->
        String value = required(attrs, 'value', 'actionSubmit')
        String action = (attrs.remove('action') ?: value).toString()
        attrs.remove('value')
        out << '<input type="submit" name="_action_' << Html.escape(action) << '" value="' << Html.escape(value) << '"' <<
                Html.attributes(attrs) << ' />'
    }

    /**
     * {@code <g:datePicker name="data" value="${d}" precision="day|hour|minute" years="${2020..2030}" noSelection="['':'-']"/>}:
     * selects {@code nome_day}, {@code nome_month}, {@code nome_year} (e hora/minuto) + {@code nome=date.struct},
     * que o data binding converte em Date.
     */
    def datePicker = { Map attrs ->
        String name = required(attrs, 'name', 'datePicker')
        String precision = attrs.precision?.toString() ?: 'minute'
        Object value = attrs.containsKey('value') ? attrs.value : new Date()
        Calendar c = null
        if (value instanceof Date) {
            c = Calendar.instance
            c.time = (Date) value
        }
        Map noSelection = attrs.noSelection instanceof Map ? (Map) attrs.noSelection : null
        int year = Calendar.instance.get(Calendar.YEAR)
        Iterable years = (Iterable) (attrs.years ?: ((year - 100)..(year + 100)))
        List<String> months = new java.text.DateFormatSymbols(webRequest?.locale ?: Locale.default).months.findAll { it } as List<String>
        String id = attrs.id ?: name
        Closure sel = { String part, Iterable options, Integer selected, Closure<String> label ->
            out << '<select name="' << Html.escape(name) << '_' << part << '" id="' << Html.escape(id) << '_' << part << '">'
            if (noSelection) out << '<option value="' << Html.escape(noSelection.keySet().first()) << '">' << Html.escape(noSelection.values().first()) << '</option>'
            options.each { o ->
                int v = o as int
                out << '<option value="' << v << '"' << (selected == v ? ' selected="selected"' : '') << '>' << Html.escape(label(v)) << '</option>'
            }
            out << '</select>'
        }
        out << '<input type="hidden" name="' << Html.escape(name) << '" value="date.struct" />'
        sel('day', 1..31, c?.get(Calendar.DAY_OF_MONTH), { it.toString().padLeft(2, '0') })
        sel('month', 1..12, c ? c.get(Calendar.MONTH) + 1 : null, { months[it - 1] })
        sel('year', years, c?.get(Calendar.YEAR), { it.toString() })
        if (precision in ['hour', 'minute']) sel('hour', 0..23, c?.get(Calendar.HOUR_OF_DAY), { it.toString().padLeft(2, '0') })
        if (precision == 'minute') sel('minute', 0..59, c?.get(Calendar.MINUTE), { it.toString().padLeft(2, '0') })
    }

    def textArea = { Map attrs, body ->
        String name = required(attrs, 'name', 'textArea')
        Object value = attrs.containsKey('value') ? attrs.remove('value') : body()
        if (!attrs.id) attrs.id = name
        out << '<textarea name="' << Html.escape(name) << '"' << Html.attributes(attrs) << '>' << Html.escape(value) << '</textarea>'
    }

    /** Gera também {@code _nome} para que o binding saiba que o checkbox desmarcado é false. */
    def checkBox = { Map attrs ->
        String name = required(attrs, 'name', 'checkBox')
        boolean checked = attrs.containsKey('checked') ? truthy(attrs.remove('checked')) : truthy(attrs.remove('value'))
        attrs.remove('value')
        if (!attrs.id) attrs.id = name
        out << '<input type="hidden" name="_' << Html.escape(name) << '" />'
        out << '<input type="checkbox" name="' << Html.escape(name) << '" value="true"' << (checked ? ' checked="checked"' : '') << Html.attributes(attrs) << ' />'
    }

    def radio = { Map attrs ->
        String name = required(attrs, 'name', 'radio')
        boolean checked = truthy(attrs.remove('checked'))
        out << '<input type="radio" name="' << Html.escape(name) << '"' << (checked ? ' checked="checked"' : '') << Html.attributes(attrs) << ' />'
    }

    /**
     * {@code <g:select name="x" from="${lista}" optionKey="id" optionValue="nome"
     * value="${sel}" noSelection="${['': '-']}"/>} (optionKey/optionValue podem ser closures).
     */
    def select = { Map attrs ->
        String name = required(attrs, 'name', 'select')
        Object from = attrs.remove('from') ?: []
        Object optionKey = attrs.remove('optionKey')
        Object optionValue = attrs.remove('optionValue')
        Object value = attrs.remove('value')
        Map noSelection = (Map) attrs.remove('noSelection')
        if (!attrs.id) attrs.id = name
        Set<String> selected = (value instanceof Collection ? value : (value == null ? [] : [value])).collect { it.toString() } as Set
        out << '<select name="' << Html.escape(name) << '"' << Html.attributes(attrs) << '>'
        noSelection?.each { k, v -> option(k, v, selected.contains(k.toString())) }
        if (from instanceof Map) {
            ((Map) from).each { k, v -> option(k, v, selected.contains(k.toString())) }
        } else {
            for (Object item : (Collection) from) {
                Object key = extract(item, optionKey)
                option(key, extract(item, optionValue), selected.contains(key?.toString()))
            }
        }
        out << '</select>'
    }

    private void option(Object key, Object label, boolean selected) {
        out << '<option value="' << Html.escape(key) << '"' << (selected ? ' selected="selected"' : '') << '>' << Html.escape(label) << '</option>'
    }

    private static Object extract(Object item, Object how) {
        if (how == null) return item
        how instanceof Closure ? ((Closure) how).call(item) : item[how.toString()]
    }

    private void input(String type, Map attrs) {
        String name = required(attrs, 'name', type + 'Field')
        if (!attrs.id && type != 'hidden') attrs.id = name
        Object value = attrs.remove('value')
        out << '<input type="' << type << '" name="' << Html.escape(name) << '"' <<
                (value != null ? ' value="' + Html.escape(value) + '"' : '') << Html.attributes(attrs) << ' />'
    }

    private static String required(Map attrs, String attr, String tag) {
        Object v = attrs.remove(attr)
        if (v == null) throw new IllegalArgumentException("<g:${tag}> exige o atributo ${attr}")
        v.toString()
    }

    private static boolean truthy(Object v) {
        v instanceof CharSequence ? v.toString() in ['true', 'on', 'checked', 'yes', '1'] : v as boolean
    }
}
