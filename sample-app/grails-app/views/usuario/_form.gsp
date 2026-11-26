<g:hasErrors bean="${usuario}">
    <ul class="erros">
        <g:eachError bean="${usuario}" var="erro">
            <li><g:message error="${erro}"/></li>
        </g:eachError>
    </ul>
</g:hasErrors>
<p class="${hasErrors(bean: usuario, field: 'nome', 'com-erro')}">
    <label for="nome"><g:message code="usuario.nome"/></label>
    <g:textField name="nome" value="${fieldValue(bean: usuario, field: 'nome')}" required="${true}"/>
</p>
<p class="${hasErrors(bean: usuario, field: 'email', 'com-erro')}">
    <label for="email"><g:message code="usuario.email"/></label>
    <g:field type="email" name="email" value="${fieldValue(bean: usuario, field: 'email')}"/>
</p>
<p class="${hasErrors(bean: usuario, field: 'idade', 'com-erro')}">
    <label for="idade"><g:message code="usuario.idade"/></label>
    <g:field type="number" name="idade" value="${fieldValue(bean: usuario, field: 'idade')}" min="0"/>
</p>
<p>
    <label for="grupo"><g:message code="usuario.grupo"/></label>
    <g:select name="grupo.id" id="grupo" from="${grupos}" optionKey="id" optionValue="nome"
              value="${usuario?.grupo?.id}" noSelection="${['': '—']}"/>
</p>
