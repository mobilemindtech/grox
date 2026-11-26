package org.grox.web

import org.springframework.beans.PropertyEditorRegistrar
import org.springframework.beans.PropertyEditorRegistry

import java.beans.PropertyEditor

/**
 * Editores dos beans {@code PropertyEditorRegistrar} da aplicação, usados pelo {@link org.grox.data.binding.DataBinder} na
 * conversão de texto. O registro é feito uma vez por requisição (PropertyEditor não é thread-safe e
 * o registrar pode usar o locale corrente).
 */
class CustomEditors implements PropertyEditorRegistry {

    static volatile List<PropertyEditorRegistrar> registrars = []

    /** {@link #find} como função, para {@code Conversions.convert}. */
    static final java.util.function.Function<Class<?>, PropertyEditor> LOOKUP = { Class<?> t -> find(t) } as java.util.function.Function<Class<?>, PropertyEditor>
    private static final String ATTR = 'grox.customEditors'

    private final Map<Class<?>, PropertyEditor> editors = [:]

    @Override
    void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor) {
        editors[requiredType] = propertyEditor
    }

    @Override
    void registerCustomEditor(Class<?> requiredType, String propertyPath, PropertyEditor propertyEditor) {
        editors[requiredType] = propertyEditor   // por caminho: não diferenciado no Grox
    }

    @Override
    PropertyEditor findCustomEditor(Class<?> requiredType, String propertyPath) {
        editors[requiredType]
    }

    /** Editor para o tipo, ou null (sem registrars, sem custo). */
    static PropertyEditor find(Class<?> type) {
        if (registrars.isEmpty()) return null
        def wr = RequestContext.currentOrNull()
        CustomEditors current = (CustomEditors) wr?.request?.getAttribute(ATTR)
        if (current == null) {
            current = new CustomEditors()
            registrars.each { it.registerCustomEditors(current) }
            wr?.request?.setAttribute(ATTR, current)
        }
        current.editors[type]
    }
}
