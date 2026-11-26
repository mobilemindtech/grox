package org.springframework.web.context.request

interface RequestAttributes {
    int SCOPE_REQUEST = 0
    int SCOPE_SESSION = 1
    Object getAttribute(String name, int scope)
    void setAttribute(String name, Object value, int scope)
    void removeAttribute(String name, int scope)
}
