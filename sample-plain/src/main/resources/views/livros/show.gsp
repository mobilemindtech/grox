<html>
<head><meta name="layout" content="main"/><title>${livro.titulo}</title></head>
<body>
<h1>${livro.titulo}</h1>
<p>${livro.autor}<g:if test="${livro.ano}">, ${livro.ano}</g:if></p>
<p>rota: ${controllerName}/${actionName}</p>
</body>
</html>
