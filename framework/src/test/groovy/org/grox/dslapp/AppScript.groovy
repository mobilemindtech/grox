package org.grox.dslapp

import groovy.transform.BaseScript
import org.grox.dsl.GroxScript

@BaseScript GroxScript base

// executado diretamente, subiria a app; no ProjectDslTest o script só é lido (GroxScript.CAPTURING)
grox {
    config {
        grox { routes { poolSize = 4 } }
        server.port = 9999
    }
    security {
        user 'ana', password: '{noop}ana', roles: ['ROLE_X']
        rule '/x/**', 'ROLE_X'
        rule '/y', httpMethod: 'GET', 'permitAll', 'isAuthenticated()'
        useBasicAuth = true
        rest.active = false
    }
    routes {
        get('/oi', access: 'permitAll') { render text: 'oi' }
    }
    beans {
        saudacao = 'olá'
    }
}
