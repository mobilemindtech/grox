// Asset principal (convenção do asset-pipeline do Grails)
//= require util
//= require_self

document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('[data-confirmar]').forEach(el => el.addEventListener('submit', e => {
        if (!confirm(el.dataset.confirmar)) e.preventDefault()
    }))
    grox.marcarLinkAtual()
})
