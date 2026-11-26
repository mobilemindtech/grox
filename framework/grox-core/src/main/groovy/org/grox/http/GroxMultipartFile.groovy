package org.grox.http

import org.springframework.web.multipart.MultipartFile

import java.nio.file.Files
import java.nio.file.Path

/** Arquivo enviado em {@code multipart/form-data} (em memória). */
class GroxMultipartFile implements MultipartFile {

    final String name
    final String originalFilename
    final String contentType
    final byte[] bytes

    GroxMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
        this.name = name
        this.originalFilename = originalFilename
        this.contentType = contentType
        this.bytes = bytes
    }

    @Override
    boolean isEmpty() {
        bytes.length == 0
    }

    @Override
    long getSize() {
        bytes.length
    }

    @Override
    InputStream getInputStream() {
        new ByteArrayInputStream(bytes)
    }

    @Override
    void transferTo(File dest) {
        dest.bytes = bytes
    }

    @Override
    void transferTo(Path dest) {
        Files.write(dest, bytes)
    }

    @Override
    String toString() {
        "MultipartFile(${name}: ${originalFilename}, ${contentType}, ${size} bytes)"
    }
}
