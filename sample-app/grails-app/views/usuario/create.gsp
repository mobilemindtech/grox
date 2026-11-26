<html>
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="usuario.novo"/></title>
</head>
<body>
<h1><g:message code="usuario.novo"/></h1>
<g:form action="save">
    <g:render template="form" model="${[usuario: usuario, grupos: grupos]}"/>
    <g:submitButton name="salvar" value="${message(code: 'acao.salvar')}"/>
</g:form>
</body>
</html>
