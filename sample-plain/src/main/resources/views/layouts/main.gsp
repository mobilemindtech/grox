<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8"/>
    <title><g:layoutTitle default="Livros"/></title>
    <g:layoutHead/>
</head>
<body>
<header><a href="/livros">Livros</a><g:if test="${request.userPrincipal}"> · ${request.userPrincipal.name} <g:form uri="/logoff" method="POST" style="display:inline"><button>Sair</button></g:form></g:if></header>
<g:if test="${flash.message}"><p class="flash">${flash.message}</p></g:if>
<g:layoutBody/>
</body>
</html>
