package org.grox.core

import java.lang.reflect.Field
import groovy.transform.CompileStatic

import java.lang.reflect.Modifier

@CompileStatic
class ClassUtils {

    /** Lê uma propriedade estática ({@code static allowedMethods = [...]}) da classe ou superclasses. */
    static Object staticProperty(Class<?> type, String name) {
        for (Class<?> k = type; k != null && k != Object; k = k.superclass) {
            Field f = null
            for (Field d : k.declaredFields) {
                if (d.name == name && Modifier.isStatic(d.modifiers)) { f = d; break }
            }
            if (f != null) {
                f.accessible = true
                return f.get(null)
            }
        }
        null
    }

    /** Classe real do usuário, sem proxies (Hibernate, ProxyGenerator). */
    static Class<?> userClass(Class<?> type) {
        Class<?> k = type
        while (k != null && k.superclass != null && (k.name.contains('$HibernateProxy') || k.name.contains('$ByteBuddy$') || k.name.contains('_groovyProxy'))) {
            k = k.superclass
        }
        k
    }

    /** {@code UsuarioService} → {@code usuarioService}; {@code URLService} → {@code URLService}. */
    static String propertyName(Class<?> type) {
        uncapitalize(userClass(type).simpleName)
    }

    /** {@code UsuarioController} + {@code Controller} → {@code usuario}. */
    static String logicalName(Class<?> type, String suffix) {
        String n = type.simpleName
        if (n.endsWith(suffix) && n.length() > suffix.length()) n = n.substring(0, n.length() - suffix.length())
        uncapitalize(n)
    }

    static String uncapitalize(String n) {
        if (!n) return n
        if (n.length() > 1 && Character.isUpperCase(n.charAt(0)) && Character.isUpperCase(n.charAt(1))) return n
        Character.toLowerCase(n.charAt(0)).toString() + n.substring(1)
    }
}
