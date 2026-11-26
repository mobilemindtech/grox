package org.springframework.security.web.savedrequest

/** URL que o usuário tentou acessar antes do login. */
interface SavedRequest extends Serializable {
    String getRedirectUrl()

    String getMethod()
}
