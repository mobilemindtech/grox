package org.grox.security.gorm

import org.grox.core.GroxModule
import org.grox.data.GormModule
import org.grox.security.SecurityModule

/**
 * Segurança sobre o GORM: usuários em domain classes ({@code userLookup.userDomainClassName}), tokens
 * REST numa domain class ({@code rest.token.storage.useGorm}) e o tenant do usuário logado como
 * resolver padrão de multi-tenancy.
 */
class SecurityGormModule extends GroxModule {

    int getOrder() { 250 }

    @Override
    void initialize() {
        def securityModule = runtime.module(SecurityModule)
        def security = securityModule?.security
        if (!security) return
        // multi-tenancy sem resolver configurado: o tenant do usuário logado
        runtime.defaults[GormModule.TENANT_RESOLVER] = UserTenantResolver.name
        if (security.cfg.userDomainClassName) securityModule.userSources << new GormUserDetailsService(security.cfg, runtime.application.classLoader)
    }

    /** A cada geração: a domain class do token vem do classloader atual da app. */
    @Override
    void contextReady() {
        def security = runtime.module(SecurityModule)?.security
        if (security?.cfg?.restActive && security.cfg.gormTokenStorage) {
            security.tokenStore = new GormTokenStorage(security.cfg, runtime.application.classLoader)
        }
    }
}
