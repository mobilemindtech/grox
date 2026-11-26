package pl.touk.excel.export

/** {@link XlsxExporter} com os cabeçalhos HTTP de download ({@code setResponseHeaders(response)}). */
class WebXlsxExporter extends XlsxExporter {

    static final String CONTENT_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'

    WebXlsxExporter() { super() }

    WebXlsxExporter(String worksheetName) { super(worksheetName) }

    WebXlsxExporter setResponseHeaders(Object response, String fileName = 'export.xlsx') {
        response.contentType = CONTENT_TYPE
        response.setHeader('Content-Disposition', org.grox.web.ControllerSupport.contentDisposition(fileName))
        this
    }
}
