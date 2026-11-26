package sample

import grails.gorm.transactions.Transactional

/** Service transacional: cada método público roda em uma transação (proxy). */
@Transactional
class UsuarioService {

    List<Usuario> listar(Map params) {
        consulta(params.q?.toString()).list(params)
    }

    long contar(String q = null) {
        consulta(q).count()
    }

    /** where { } com filtro opcional (nome ou e-mail, sem diferenciar maiúsculas). */
    private static def consulta(String q) {
        if (!q) return Usuario.where { id != null }
        String termo = "%${q.toLowerCase()}%"
        Usuario.where { nome =~ termo || email =~ termo }
    }

    boolean salvar(Usuario usuario) {
        usuario.save() != null
    }

    boolean remover(Long id) {
        Usuario usuario = Usuario.get(id)
        if (!usuario) return false
        usuario.delete()
        true
    }
}
