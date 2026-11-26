package org.grox.view.gsp

class GspException extends RuntimeException {

    final String view
    final int line

    GspException(String view, int line, String message, Throwable cause = null) {
        super("${view ?: 'GSP'}${line > 0 ? ":${line}" : ''}: ${message}", cause)
        this.view = view
        this.line = line
    }
}
