<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>Relatório</title></head>
<body>
<g:if test="${logo}"><rendering:inlinePng bytes="${logo}" height="10px"/></g:if>
<h1>Relatório de ${titulo}</h1>
<ul><g:each in="${itens}" var="i"><li>${i}</li></g:each></ul>
</body>
</html>
