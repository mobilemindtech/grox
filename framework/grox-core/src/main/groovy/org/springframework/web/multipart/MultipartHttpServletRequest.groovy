package org.springframework.web.multipart

/**
 * Compatibilidade com o {@code MultipartHttpServletRequest} do Spring: o {@code request} do Grox o implementa
 * ({@code request instanceof MultipartHttpServletRequest}, {@code getFile}, {@code getFiles}, {@code getFileMap}).
 * Só é instância "de fato" multipart quando a requisição é multipart ({@code request.multipart}).
 */
interface MultipartHttpServletRequest {
    Object getFile(String name)

    List getFiles(String name)

    Iterator<String> getFileNames()

    Map<String, Object> getFileMap()
}
