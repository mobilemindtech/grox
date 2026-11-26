package org.grox.web

import org.grox.core.Autowirer
import org.grox.data.Gorm
import org.grox.data.binding.DataBinder
import org.springframework.web.multipart.MultipartFile

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Uma action e seus parâmetros, vinculados por nome a params/JSON (como no Grails). */
class ActionMethod {

    final String name
    final Method method
    final List<String> parameterNames
    final Class<?>[] parameterTypes
    /** Action como propriedade closure ({@code def list = { ... }}, estilo Grails 2): nome do campo. */
    final String closureField

    /** Action closure: sem parâmetros; o corpo usa {@code params}, como no Grails. */
    ActionMethod(String closureField) {
        this.method = null
        this.name = closureField
        this.closureField = closureField
        this.parameterTypes = new Class<?>[0]
        this.parameterNames = []
    }

    ActionMethod(Method method) {
        this.closureField = null
        this.method = method
        this.name = method.name
        this.parameterTypes = method.parameterTypes
        Action a = method.getAnnotation(Action)
        List<String> names = a?.parameterNames()?.toList()
        this.parameterNames = names && names.size() == parameterTypes.length ? names : method.parameters*.name
        method.accessible = true
    }

    @groovy.transform.CompileStatic
    Object[] bindArguments(WebRequest wr, Autowirer autowirer) {
        Object[] args = new Object[parameterTypes.length]
        for (int i = 0; i < args.length; i++) {
            Class<?> type = parameterTypes[i]
            String n = parameterNames[i]
            if (MultipartFile.isAssignableFrom(type)) {
                args[i] = wr.request.getFile(n)
            } else if (DataBinder.isSimple(type)) {
                args[i] = DataBinder.bindValue(wr.params.get(n), type)
            } else if (Map.isAssignableFrom(type)) {
                args[i] = wr.request.jsonRequest ? wr.request.JSON : wr.params
            } else {
                args[i] = commandObject(type, wr, autowirer)
            }
        }
        args
    }

    private static Object commandObject(Class<?> type, WebRequest wr, Autowirer autowirer) {
        Object cmd
        if (Gorm.isDomain(type) && wr.params.get('id')) {
            // como no Grails: domain com id na requisição é carregada e então recebe o binding
            cmd = Gorm.api(type).get(wr.params.get('id') instanceof List ? ((List) wr.params.get('id'))[0] : wr.params.get('id'))
            if (cmd == null) return null
        } else {
            cmd = type.getDeclaredConstructor().newInstance()
        }
        autowirer?.autowire(cmd)
        Object source = wr.request.jsonRequest ? wr.request.JSON : wr.params
        DataBinder.bind(cmd, source)
        if (cmd.respondsTo('validate')) cmd.invokeMethod('validate', null)
        cmd
    }

    @groovy.transform.CompileStatic
    Object invoke(Object target, Object[] args) {
        if (closureField != null) {
            // a closure da própria instância: owner é o controller (params, render, services...)
            return ((Closure) org.codehaus.groovy.runtime.InvokerHelper.getProperty(target, closureField)).call()
        }
        try {
            method.invoke(target, args)
        } catch (InvocationTargetException e) {
            throw e.cause
        }
    }
}
