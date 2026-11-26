package org.grox.security

/** Resultado de uma autenticação: o usuário e o mecanismo (form, basic, bearer). */
class Authentication implements Serializable, org.grox.session.SessionValue, org.springframework.security.core.Authentication {

    private static final long serialVersionUID = 1L

    GroxUser principal
    String method
    /** Dados do token (jti, exp) para revogação no logout REST. */
    Map credentials

    /** Imutável depois de criada: o login grava uma nova com {@code setAttribute}. */
    @Override
    boolean isModified() {
        false
    }

    Object getDetails() { null }

    boolean isAuthenticated() { principal != null }

    String getName() {
        principal?.username
    }

    Set<GrantedAuthority> getAuthorities() {
        principal?.authorities ?: [] as Set
    }

    boolean hasAuthority(String authority) {
        principal != null && principal.hasAuthority(authority)
    }

    /** Basic e Bearer são sem estado (não usam sessão). CSRF: ver {@code SecurityFilter.csrfExempt}. */
    boolean isStateless() {
        method in ['basic', 'bearer']
    }
}
