<html>
<head><meta name="layout" content="main"/><title>Livros</title></head>
<body>
<h1>Livros</h1>
<ul>
<g:each in="${livros}" var="l"><li><g:link uri="/livros/${l.id}">${l.titulo}</g:link> (${l.autor})</li></g:each>
</ul>
<g:hasErrors bean="${novo}"><p class="erro">Corrija os campos destacados</p></g:hasErrors>
<g:form uri="/livros" method="POST">
    <input name="titulo" value="${novo?.titulo}"/> <input name="autor" value="${novo?.autor}"/> <input name="ano" value="${novo?.ano}"/>
    <button>Salvar</button>
</g:form>
</body>
</html>
