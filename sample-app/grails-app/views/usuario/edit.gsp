<html>
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="acao.editar"/> ${usuario.nome}</title>
</head>
<body>
<h1><g:message code="acao.editar"/> ${usuario.nome}</h1>
<g:form action="update" id="${usuario.id}" method="PUT">
    <g:render template="form" model="${[usuario: usuario, grupos: grupos]}"/>
    <g:submitButton name="salvar" value="${message(code: 'acao.salvar')}"/>
</g:form>
</body>
</html>
