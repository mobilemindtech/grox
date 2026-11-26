<html>
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="tarefa.titulo"/></title>
</head>
<body>
<h1><g:message code="tarefa.titulo"/> <fmt:badge><sec:loggedInUserInfo field="tenantId"/></fmt:badge></h1>
<ul class="tarefas">
    <g:each in="${tarefaList}" var="t">
        <li class="${t.concluida ? 'feita' : ''}">${t.titulo}</li>
    </g:each>
</ul>
<g:form action="save">
    <g:renderErrors bean="${tarefa}"/>
    <g:textField name="titulo" value="${fieldValue(bean: tarefa, field: 'titulo')}"/>
    <g:submitButton name="adicionar" value="${message(code: 'tarefa.adicionar')}"/>
</g:form>
</body>
</html>
