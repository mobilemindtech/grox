<!DOCTYPE html>
<html lang="${locale?.language ?: 'pt'}">
<head>
    <meta charset="utf-8"/>
    <title><g:layoutTitle default="Grox"/> · Grox Sample</title>
    <asset:stylesheet href="application.css"/>
    <asset:javascript src="application.js" defer="${true}"/>
    <asset:link rel="icon" href="logo.svg" type="image/svg+xml"/>
    <g:layoutHead/>
</head>
<body>
<header>
    <nav>
        <g:link uri="/" class="marca">Grox</g:link>
        <g:link controller="usuario"><g:message code="usuario.lista.titulo"/></g:link>
        <g:link controller="usuario" action="create"><g:message code="usuario.novo"/></g:link>
        <sec:ifLoggedIn><g:link controller="tarefa"><g:message code="tarefa.titulo"/></g:link></sec:ifLoggedIn>
        <span class="idiomas"><a href="?lang=pt_BR">pt</a> · <a href="?lang=en">en</a></span>
        <sec:ifLoggedIn>
            <span class="usuario"><sec:username/></span>
            <g:form url="/logoff" class="sair"><g:submitButton name="sair" value="${message(code: 'acao.sair')}"/></g:form>
        </sec:ifLoggedIn>
        <sec:ifNotLoggedIn><a href="/login/auth"><g:message code="acao.entrar"/></a></sec:ifNotLoggedIn>
    </nav>
    <g:pageProperty name="page.submenu"/>
</header>
<main>
    <g:if test="${flash.message}">
        <div class="mensagem">${flash.message}</div>
    </g:if>
    <g:layoutBody/>
</main>
<footer>Groovy ${GroovySystem.version} · Netty · SiteMesh 3</footer>
</body>
</html>
