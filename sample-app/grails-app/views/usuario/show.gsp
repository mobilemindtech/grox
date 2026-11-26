<html>
<head>
    <meta name="layout" content="main"/>
    <title>${usuario.nome}</title>
</head>
<body>
<h1>${usuario.nome} <fmt:badge>#${usuario.id}</fmt:badge></h1>
<dl>
    <dt><g:message code="usuario.email"/></dt><dd>${usuario.email}</dd>
    <dt><g:message code="usuario.idade"/></dt><dd><fmt:idade valor="${usuario.idade}"/></dd>
    <dt><g:message code="usuario.grupo"/></dt><dd>${usuario.grupo?.nome ?: '—'}</dd>
    <dt><g:message code="usuario.criadoEm"/></dt><dd><g:formatDate date="${usuario.dateCreated}" format="dd/MM/yyyy HH:mm"/></dd>
</dl>
<g:if test="${usuario.foto}">
    <img src="${createLink(action: 'foto', id: usuario.id)}" alt="foto" class="foto"/>
</g:if>
<sec:ifAllGranted roles="ROLE_ADMIN">
<g:uploadForm action="enviarFoto" id="${usuario.id}" class="upload">
    <input type="file" name="foto" accept="image/*"/>
    <g:submitButton name="enviar" value="${message(code: 'usuario.foto.enviar')}"/>
</g:uploadForm>
</sec:ifAllGranted>
<g:form action="delete" id="${usuario.id}" method="DELETE" data-confirmar="${message(code: 'usuario.confirmarRemocao')}">
    <g:link action="edit" id="${usuario.id}"><g:message code="acao.editar"/></g:link>
    <g:submitButton name="remover" value="${message(code: 'acao.remover')}"/>
</g:form>
</body>
</html>
