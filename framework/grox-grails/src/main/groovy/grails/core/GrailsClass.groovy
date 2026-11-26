package grails.core

import groovy.transform.CompileStatic
import org.grox.core.ClassUtils

/** Descrição de um artefato ({@code grailsApplication.controllerClasses} etc.). */
@CompileStatic
class GrailsClass {
    final Class<?> clazz
    final String type

    GrailsClass(Class<?> clazz, String type) {
        this.clazz = clazz
        this.type = type
    }

    String getName() { logicalName(clazz.simpleName) }
    String getShortName() { clazz.simpleName }
    String getFullName() { clazz.name }
    String getPackageName() { clazz.packageName }
    String getPropertyName() { uncapitalize(clazz.simpleName) }
    String getLogicalPropertyName() { uncapitalize(logicalName(clazz.simpleName)) }
    /** {@code static namespace} do artefato (controllers), ou null. */
    String getNamespace() { ClassUtils.staticProperty(clazz, 'namespace')?.toString() }
    String getNaturalName() { logicalName(clazz.simpleName).replaceAll(/([a-z])([A-Z])/, '$1 $2') }

    Object newInstance() { clazz.getDeclaredConstructor().newInstance() }

    private String logicalName(String simple) {
        type && simple.endsWith(type) && simple != type ? simple.substring(0, simple.length() - type.length()) : simple
    }

    private static String uncapitalize(String s) {
        s ? s.substring(0, 1).toLowerCase() + s.substring(1) : s
    }

    @Override
    String toString() { "Artefact > ${clazz.name}" }
}
