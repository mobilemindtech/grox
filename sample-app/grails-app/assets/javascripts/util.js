window.grox = {
    marcarLinkAtual() {
        document.querySelectorAll('header nav a').forEach(a => {
            if (a.pathname !== '/' && location.pathname.startsWith(a.pathname)) a.classList.add('atual')
        })
    }
}
