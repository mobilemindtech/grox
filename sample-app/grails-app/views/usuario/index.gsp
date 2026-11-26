<html>
<head>
    <meta name="layout" content="main"/>
    <title><g:message code="usuario.lista.titulo"/></title>
</head>
<body>
<content tag="submenu"><p class="submenu"><g:message code="usuario.total" args="${[total]}"/></p></content>
<h1><g:message code="usuario.lista.titulo"/></h1>
<g:form action="index" method="GET" class="busca">
    <g:textField name="q" value="${params.q}" placeholder="${message(code: 'usuario.busca')}"/>
    <g:submitButton name="buscar" value="${message(code: 'acao.buscar')}"/>
</g:form>
<g:if test="${usuarioList}">
    <table>
        <thead><tr>
            <g:sortableColumn property="id" title="#" params="${[q: params.q]}"/>
            <g:sortableColumn property="nome" titleKey="usuario.nome" params="${[q: params.q]}"/>
            <g:sortableColumn property="email" titleKey="usuario.email" params="${[q: params.q]}"/>
            <g:sortableColumn property="idade" titleKey="usuario.idade" defaultOrder="desc" params="${[q: params.q]}"/>
            <th><g:message code="usuario.grupo"/></th>
        </tr></thead>
        <tbody>
        <g:each in="${usuarioList}" var="u" status="i">
            <tr class="${i % 2 == 0 ? 'par' : 'impar'}">
                <td>${u.id}</td>
                <td><g:link action="show" id="${u.id}">${u.nome}</g:link></td>
                <td>${u.email}</td>
                <td><fmt:idade valor="${u.idade}"/></td>
                <td>${u.grupo?.nome ?: '—'}</td>
            </tr>
        </g:each>
        </tbody>
    </table>
    <div class="paginacao"><g:paginate total="${total}" params="${[q: params.q]}"/></div>
    <p class="rodape-lista"><g:formatDate date="${interceptadoEm}" format="HH:mm:ss"/></p>
</g:if>
<g:else>
    <p class="vazio"><g:message code="usuario.nenhum"/></p>
</g:else>
</body>
</html>
