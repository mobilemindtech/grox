<html>
<head><meta name="layout" content="main"/><title>Entrar</title></head>
<body>
<h1>Entrar no sample-plain</h1>
<g:if test="${errorMessage}"><p class="erro">${errorMessage}</p></g:if>
<form action="${postUrl}" method="post">
    <input type="hidden" name="${csrfParameter}" value="${csrfToken}"/>
    <input name="username"/> <input name="password" type="password"/> <button>Entrar</button>
</form>
</body>
</html>
