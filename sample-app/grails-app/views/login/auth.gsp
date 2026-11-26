<html>
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="springSecurity.login.title"/></title>
</head>
<body>
<h1><g:message code="springSecurity.login.header"/></h1>
<p class="login-grupos"><fmt:grupos/></p>
<p class="login-rota">${params.controller}/${params.action}|${controllerName}/${actionName}</p>
<g:if test="${errorMessage}">
    <p class="login-erro">${errorMessage}</p>
</g:if>
<form action="${postUrl}" method="post" class="login">
    <p><label for="username"><g:message code="springSecurity.login.username.label"/></label>
        <input type="text" name="${usernameParameter}" id="username" autofocus/></p>
    <p><label for="password"><g:message code="springSecurity.login.password.label"/></label>
        <input type="password" name="${passwordParameter}" id="password"/></p>
    <input type="hidden" name="${csrfParameter}" value="${csrfToken}"/>
    <input type="submit" value="${message(code: 'springSecurity.login.button')}"/>
</form>
</body>
</html>
