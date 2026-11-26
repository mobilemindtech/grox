package org.grox.view.gsp

/**
 * Localiza {@code <path>.gsp} (ou outra extensão, para os {@link org.grox.view.ViewEngine}): primeiro no diretório de desenvolvimento
 * ({@code grails-app/views}, com reload), depois no classpath ({@code views/}).
 */
class ViewLocator {

    static class ViewSource {
        File file
        URL url
        /** Fonte gerada em memória (views de scaffold), com o nome que aparece nos erros. */
        String content
        String name

        String getText() {
            content != null ? content : file ? file.getText('UTF-8') : url.getText('UTF-8')
        }

        long getLastModified() {
            file ? file.lastModified() : 0L
        }

        String toString() {
            content != null ? name : file ? file.path : url.toString()
        }
    }

    final File directory
    final ClassLoader loader

    ViewLocator(File directory, ClassLoader loader) {
        this.directory = directory
        this.loader = loader
    }

    ViewSource find(String path, String extension = 'gsp') {
        if (!path || path.contains('..')) return null
        String rel = (path.startsWith('/') ? path.substring(1) : path) + '.' + extension
        if (directory != null) {
            File f = new File(directory, rel)
            if (f.file) return new ViewSource(file: f)
        }
        URL u = loader.getResource('views/' + rel)
        u ? new ViewSource(url: u) : null
    }
}
