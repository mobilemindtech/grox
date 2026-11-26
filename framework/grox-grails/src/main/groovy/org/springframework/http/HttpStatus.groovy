package org.springframework.http

import groovy.transform.CompileStatic

/**
 * Compatibilidade com {@code org.springframework.http.HttpStatus} (subconjunto usual).
 * {@code render/respond status: NOT_FOUND} aceitam o enum (ver {@code ControllerSupport.statusCode}).
 */
@CompileStatic
enum HttpStatus {
    CONTINUE(100, 'Continue'),
    SWITCHING_PROTOCOLS(101, 'Switching Protocols'),
    OK(200, 'OK'),
    CREATED(201, 'Created'),
    ACCEPTED(202, 'Accepted'),
    NO_CONTENT(204, 'No Content'),
    PARTIAL_CONTENT(206, 'Partial Content'),
    MOVED_PERMANENTLY(301, 'Moved Permanently'),
    FOUND(302, 'Found'),
    SEE_OTHER(303, 'See Other'),
    NOT_MODIFIED(304, 'Not Modified'),
    TEMPORARY_REDIRECT(307, 'Temporary Redirect'),
    PERMANENT_REDIRECT(308, 'Permanent Redirect'),
    BAD_REQUEST(400, 'Bad Request'),
    UNAUTHORIZED(401, 'Unauthorized'),
    PAYMENT_REQUIRED(402, 'Payment Required'),
    FORBIDDEN(403, 'Forbidden'),
    NOT_FOUND(404, 'Not Found'),
    METHOD_NOT_ALLOWED(405, 'Method Not Allowed'),
    NOT_ACCEPTABLE(406, 'Not Acceptable'),
    REQUEST_TIMEOUT(408, 'Request Timeout'),
    CONFLICT(409, 'Conflict'),
    GONE(410, 'Gone'),
    PRECONDITION_FAILED(412, 'Precondition Failed'),
    PAYLOAD_TOO_LARGE(413, 'Payload Too Large'),
    UNSUPPORTED_MEDIA_TYPE(415, 'Unsupported Media Type'),
    UNPROCESSABLE_ENTITY(422, 'Unprocessable Entity'),
    TOO_MANY_REQUESTS(429, 'Too Many Requests'),
    INTERNAL_SERVER_ERROR(500, 'Internal Server Error'),
    NOT_IMPLEMENTED(501, 'Not Implemented'),
    BAD_GATEWAY(502, 'Bad Gateway'),
    SERVICE_UNAVAILABLE(503, 'Service Unavailable'),
    GATEWAY_TIMEOUT(504, 'Gateway Timeout')

    private final int code
    final String reasonPhrase

    HttpStatus(int code, String reasonPhrase) {
        this.code = code
        this.reasonPhrase = reasonPhrase
    }

    int value() { code }

    boolean is2xxSuccessful() { code >= 200 && code < 300 }
    boolean is4xxClientError() { code >= 400 && code < 500 }
    boolean is5xxServerError() { code >= 500 }
    boolean isError() { code >= 400 }

    static HttpStatus valueOf(int code) {
        HttpStatus s = values().find { it.code == code }
        if (s == null) throw new IllegalArgumentException("Status HTTP desconhecido: $code")
        s
    }

    @Override
    String toString() { "$code ${name()}" }
}
