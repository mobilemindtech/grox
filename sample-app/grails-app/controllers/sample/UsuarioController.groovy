package sample

import grails.converters.JSON

/** CRUD HTML (views GSP) e JSON (/api/usuarios) no mesmo controller, como no Grails. */
class UsuarioController {

    static responseFormats = ['html', 'json']
    static allowedMethods = [save: 'POST', update: ['PUT', 'POST'], delete: ['DELETE', 'POST'], enviarFoto: 'POST']

    UsuarioService usuarioService
    EstatisticaService estatisticaService

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond usuarioService.listar(params), model: [total: usuarioService.contar(params.q)]
    }

    def show(Usuario usuario) {
        respond usuario
    }

    def create() {
        respond new Usuario(params), model: [grupos: Grupo.list()]
    }

    def save(Usuario usuario) {
        if (usuario.hasErrors() || !usuarioService.salvar(usuario)) {
            withFormat {
                html { render view: 'create', model: [usuario: usuario, grupos: Grupo.list()], status: 422 }
                json { respond usuario.errors, status: 422 }
            }
            return
        }
        estatisticaService.registrar('criado')
        withFormat {
            html {
                flash.message = message(code: 'usuario.criado', args: [usuario.nome])
                redirect action: 'show', id: usuario.id
            }
            json { respond usuario, status: 201 }
        }
    }

    def edit(Usuario usuario) {
        respond usuario, model: [grupos: Grupo.list()]
    }

    def update(Usuario usuario) {
        if (usuario == null) {
            notFound()
            return
        }
        if (usuario.hasErrors() || !usuarioService.salvar(usuario)) {
            usuario.discard()
            withFormat {
                html { render view: 'edit', model: [usuario: usuario, grupos: Grupo.list()], status: 422 }
                json { respond usuario.errors, status: 422 }
            }
            return
        }
        estatisticaService.registrar('atualizado')
        withFormat {
            html {
                flash.message = message(code: 'usuario.atualizado', args: [usuario.nome])
                redirect action: 'show', id: usuario.id
            }
            json { respond usuario }
        }
    }

    def delete(Long id) {
        if (!usuarioService.remover(id)) {
            notFound()
            return
        }
        estatisticaService.registrar('removido')
        withFormat {
            html {
                flash.message = message(code: 'usuario.removido')
                redirect action: 'index'
            }
            json { render status: 204 }
        }
    }

    def exportar(Long id) {
        render Usuario.get(id) as JSON
    }

    def contagem() {
        render([total: usuarioService.contar()] as JSON)
    }

    /** createCriteria com projeções: usuários por grupo e idade média. */
    def estatisticas() {
        def porGrupo = Usuario.createCriteria().list {
            grupo {
                projections {
                    groupProperty('nome')
                }
            }
            projections {
                count()
                avg('idade')
            }
            order('grupo.nome')
        }
        long semGrupo = Usuario.createCriteria().count { isNull('grupo') }
        respond([grupos: porGrupo.collect { [grupo: it[0], usuarios: it[1], idadeMedia: it[2]] }, semGrupo: semGrupo])
    }

    /** Upload multipart: {@code <g:uploadForm>} com {@code <input type="file" name="foto">}. */
    def enviarFoto(Long id) {
        Usuario usuario = Usuario.get(id)
        if (!usuario) {
            notFound()
            return
        }
        def arquivo = request.getFile('foto')
        if (arquivo == null || arquivo.empty) {
            flash.message = message(code: 'usuario.foto.vazia')
        } else {
            usuario.foto = arquivo.bytes
            usuario.fotoTipo = arquivo.contentType
            if (usuarioService.salvar(usuario)) {
                flash.message = message(code: 'usuario.foto.enviada', args: [arquivo.originalFilename, arquivo.size])
            } else {
                usuario.discard()
                flash.message = message(error: usuario.errors.fieldErrors[0])
            }
        }
        redirect action: 'show', id: id
    }

    def foto(Long id) {
        Usuario usuario = Usuario.get(id)
        if (!usuario?.foto) {
            render status: 404
            return
        }
        header 'Cache-Control', 'no-cache'
        render file: usuario.foto, contentType: usuario.fotoTipo
    }

    def maiores() {
        respond Usuario.findAllByIdadeGreaterThanEquals(18, [sort: 'idade', order: 'desc'])
    }

    protected void notFound() {
        withFormat {
            html {
                flash.message = message(code: 'usuario.naoEncontrado')
                redirect action: 'index'
            }
            json { render status: 404 }
        }
    }
}
