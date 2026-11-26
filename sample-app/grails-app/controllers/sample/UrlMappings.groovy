package sample

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?" {
            constraints {
                // restrições aplicáveis aqui
            }
        }

        "/"(controller: 'home')
        "/ola/$nome?"(controller: 'home', action: 'ola')

        group "/api", {
            "/usuarios"(resources: 'usuario', format: 'json')
        }

        "500"(controller: 'erro', action: 'interno')
        "404"(controller: 'erro', action: 'naoEncontrado')
        "403"(controller: 'erro', action: 'proibido')
    }
}
