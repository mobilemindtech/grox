package org.grox.security

import org.grox.i18n.MessageSource
import org.grox.http.Html

/** Página de login padrão (quando a aplicação não tem views/login/auth.gsp nem LoginController). */
class LoginPage {

    static String render(Map model, MessageSource messages, Locale locale) {
        Closure<String> m = { String code -> Html.escape(messages.getMessage(code, null, null, locale)) }
        String error = model.errorCode ? "<p class=\"login-error\">${m(model.errorCode)}</p>" : ''
        String csrf = model.csrfToken ? "<input type=\"hidden\" name=\"${model.csrfParameter}\" value=\"${Html.escape(model.csrfToken)}\"/>" : ''
        """<!DOCTYPE html>
<html><head><meta charset="utf-8"/><title>${m('springSecurity.login.title')}</title>
<style>body{font-family:system-ui,sans-serif;display:flex;justify-content:center;margin-top:10vh}form{min-width:18rem}
label,input{display:block;width:100%;margin:.3rem 0}input{padding:.4rem}.login-error{color:#b00020}</style></head>
<body><form action="${Html.escape(model.postUrl)}" method="post">
<h1>${m('springSecurity.login.header')}</h1>${error}
<label for="username">${m('springSecurity.login.username.label')}</label><input type="text" name="${model.usernameParameter}" id="username" autofocus/>
<label for="password">${m('springSecurity.login.password.label')}</label><input type="password" name="${model.passwordParameter}" id="password"/>
${csrf}<input type="submit" value="${m('springSecurity.login.button')}"/>
</form></body></html>"""
    }
}
