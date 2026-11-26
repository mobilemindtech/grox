package org.springframework.beans

import java.beans.PropertyEditor

/** Compatibilidade com o {@code PropertyEditorRegistry} do Spring (editores por tipo). */
interface PropertyEditorRegistry {
    void registerCustomEditor(Class<?> requiredType, PropertyEditor propertyEditor)

    void registerCustomEditor(Class<?> requiredType, String propertyPath, PropertyEditor propertyEditor)

    PropertyEditor findCustomEditor(Class<?> requiredType, String propertyPath)
}
