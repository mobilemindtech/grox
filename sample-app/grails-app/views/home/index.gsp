<html>
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="home.titulo"/></title>
</head>
<body>
<h1><g:message code="home.titulo"/></h1>
<p><g:message code="home.boasVindas" args="${[total]}"/></p>
<ul>
    <li>Thread: <code>${thread}</code></li>
    <li><g:message code="home.agora"/>: <g:formatDate date="${agora}" format="dd/MM/yyyy HH:mm"/></li>
    <li><g:message code="home.estatisticas"/>: ${estatisticas.grupos} grupo(s), eventos ${estatisticas.eventos}</li>
</ul>
</body>
</html>
