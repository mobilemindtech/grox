package grails.rest

import org.codehaus.groovy.runtime.InvokerHelper
import org.grox.web.Action
import org.grox.web.api.ControllerApi

/**
 * Compatibilidade com o {@code RestfulController} do Grails: CRUD por convenção sobre a domain class. Subclasses
 * chamam {@code super(Dominio)}; um controller com {@code static scaffold = Dominio} vira um destes (scaffold
 * dinâmico). Como no Grails, formulários HTML ({@code form}, {@code multipartForm}) recebem o redirecionamento com
 * a mensagem no flash; os outros formatos, {@code respond} com o status (201, 200, 204, 404, 422).
 */
class RestfulController<T> implements ControllerApi {

    static allowedMethods = [save: 'POST', update: ['PUT', 'POST'], patch: 'PATCH', delete: 'DELETE']

    final Class<T> resource
    final boolean readOnly

    RestfulController(Class<T> resource, boolean readOnly = false) {
        this.resource = resource
        this.readOnly = readOnly
    }

    protected String getResourceName() {
        resource.simpleName.uncapitalize()
    }

    protected String getResourceClassName() {
        resource.simpleName
    }

    /** O nome da classe nas mensagens: {@code livro.label} ou o nome simples. */
    protected String getClassMessageArg() {
        message(code: "${resourceName}.label".toString(), default: resourceClassName)
    }

    @Action(parameterNames = ['max'])
    Object index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond(listAllResources(params), [model: [("${resourceName}Count".toString()): countResources()]])
    }

    @Action
    Object show() {
        respond(queryForResource(params.id))
    }

    @Action
    Object create() {
        if (handleReadOnly()) return null
        respond(createResource())
    }

    @Action
    Object save() {
        if (handleReadOnly()) return null
        T instance = createResource()
        if (!InvokerHelper.invokeMethod(instance, 'validate', null)) return respondErrors(instance, 'create')
        saveResource(instance)
        Object id = InvokerHelper.getProperty(instance, 'id')
        if (isFormRequest()) {
            flash.message = message(code: 'default.created.message', args: [classMessageArg, id])
            return redirect(action: 'show', id: id)
        }
        response.setHeader('Location', createLink(action: 'show', id: id, absolute: true))
        respond(instance, [status: 201, view: 'show'])
    }

    @Action
    Object edit() {
        if (handleReadOnly()) return null
        respond(queryForResource(params.id))
    }

    @Action
    Object update() {
        if (handleReadOnly()) return null
        T instance = queryForResource(params.id)
        if (instance == null) return notFound()
        bindData(instance, objectToBind)
        if (!InvokerHelper.invokeMethod(instance, 'validate', null)) return respondErrors(instance, 'edit')
        updateResource(instance)
        Object id = InvokerHelper.getProperty(instance, 'id')
        if (isFormRequest()) {
            flash.message = message(code: 'default.updated.message', args: [classMessageArg, id])
            return redirect(action: 'show', id: id)
        }
        respond(instance, [status: 200, view: 'show'])
    }

    @Action
    Object delete() {
        if (handleReadOnly()) return null
        T instance = queryForResource(params.id)
        if (instance == null) return notFound()
        deleteResource(instance)
        if (isFormRequest()) {
            flash.message = message(code: 'default.deleted.message', args: [classMessageArg, params.id])
            return redirect(action: 'index')
        }
        render(status: 204)
    }

    /** Formulário HTML: {@code form} ou {@code multipartForm} pelo Content-Type (o {@code request.withFormat} do Grails). */
    protected boolean isFormRequest() {
        request.format in ['form', 'multipartForm']
    }

    /** 404; num formulário, a mensagem no flash e a lista. */
    protected Object notFound() {
        if (isFormRequest()) {
            flash.message = message(code: 'default.not.found.message', args: [classMessageArg, params.id])
            return redirect(action: 'index')
        }
        render(status: 404)
    }

    /** 422: os erros em JSON, ou a view ({@code create}/{@code edit}) com a instância. */
    protected Object respondErrors(T instance, String view) {
        respond(InvokerHelper.getProperty(instance, 'errors'), [status: 422, view: view, model: [(resourceName): instance]])
    }

    /** 405 numa operação de escrita de um controller {@code readOnly}. */
    protected boolean handleReadOnly() {
        if (!readOnly) return false
        render(status: 405)
        true
    }

    /** A origem do binding: o JSON do corpo (requisição JSON) ou os parâmetros (formulário, query). */
    protected Object getObjectToBind() {
        request.jsonRequest ? (request.JSON ?: [:]) : params
    }

    protected T queryForResource(Serializable id) {
        (T) InvokerHelper.invokeStaticMethod(resource, 'get', id)
    }

    protected T createResource() {
        T instance = resource.getDeclaredConstructor().newInstance()
        bindData(instance, objectToBind)
        instance
    }

    protected List<T> listAllResources(Map params) {
        (List<T>) InvokerHelper.invokeStaticMethod(resource, 'list', params)
    }

    protected Integer countResources() {
        ((Number) InvokerHelper.invokeStaticMethod(resource, 'count', null)).intValue()
    }

    protected T saveResource(T resource) {
        (T) InvokerHelper.invokeMethod(resource, 'save', [flush: true])
    }

    protected T updateResource(T resource) {
        saveResource(resource)
    }

    protected void deleteResource(T resource) {
        InvokerHelper.invokeMethod(resource, 'delete', [flush: true])
    }
}
