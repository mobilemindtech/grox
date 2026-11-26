package sample

/** Sem match(): intercepta o controller de mesmo nome (usuario). Acrescenta dados ao modelo das views. */
class UsuarioInterceptor {

    boolean after() {
        if (view) model.interceptadoEm = new Date()
        true
    }
}
