package pl.touk.excel.export

import org.apache.poi.ss.usermodel.BorderStyle
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Plugin excel-export (subconjunto) reimplementado sobre o Apache POI 5 (XLSX).
 * Requer {@code org.apache.poi:poi-ooxml} no classpath da aplicação.
 * <pre>
 * def xls = new XlsxExporter()
 * xls.putCellValue(0, 0, 'Nome').bold().centre()
 * xls.save(outputStream)
 * </pre>
 */
class XlsxExporter implements Closeable {

    protected final XSSFWorkbook workbook = new XSSFWorkbook()
    protected Sheet sheet
    private CellStyle dateStyle

    XlsxExporter() {
        sheet = workbook.createSheet('Report')
    }

    XlsxExporter(String worksheetName) {
        sheet = workbook.createSheet(worksheetName)
    }

    XlsxExporter setWorksheetName(String name) {
        workbook.setSheetName(workbook.getSheetIndex(sheet), name)
        this
    }

    XlsxExporter sheet(String name) {
        sheet = workbook.getSheet(name) ?: workbook.createSheet(name)
        this
    }

    Sheet getSheet() { sheet }

    XSSFWorkbook getWorkbook() { workbook }

    /** Próxima linha usada por {@link #add} (depois do cabeçalho de {@link #fillHeader}). */
    protected int nextRow = 0

    /** Cabeçalho na primeira linha, como no excel-export. */
    XlsxExporter fillHeader(List headers) {
        headers.eachWithIndex { Object h, int i -> putCellValue(0, i, h?.toString()) }
        nextRow = Math.max(nextRow, 1)
        this
    }

    /** Uma linha por valor da lista, na linha indicada. */
    XlsxExporter fillRow(List values, int row) {
        values.eachWithIndex { Object v, int i -> putCellValue(row, i, v) }
        nextRow = Math.max(nextRow, row + 1)
        this
    }

    /**
     * Uma linha por objeto, com as propriedades na ordem dada: nome ({@code 'nome'}), caminho ({@code 'cliente.nome'})
     * ou closure ({@code { it.total * 2 }}); mapas também valem.
     */
    XlsxExporter add(Iterable objects, List properties) {
        objects?.each { Object o ->
            properties.eachWithIndex { Object p, int i -> putCellValue(nextRow, i, valueOf(o, p)) }
            nextRow++
        }
        this
    }

    private static Object valueOf(Object o, Object property) {
        if (property instanceof Closure) return ((Closure) property).call(o)
        Object v = o
        for (String part : property.toString().tokenize('.')) {
            if (v == null) return null
            v = v instanceof Map ? ((Map) v).get(part) : v[part]
        }
        v
    }

    CellFormatter putCellValue(int rowIndex, int columnIndex, Object value) {
        Row row = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
        Cell cell = row.getCell(columnIndex) ?: row.createCell(columnIndex)
        setValue(cell, value)
        new CellFormatter(cell)
    }

    Object getCellAt(int rowIndex, int columnIndex) {
        Cell c = sheet.getRow(rowIndex)?.getCell(columnIndex)
        if (c == null) return null
        switch (c.cellType) {
            case org.apache.poi.ss.usermodel.CellType.NUMERIC: return c.numericCellValue
            case org.apache.poi.ss.usermodel.CellType.BOOLEAN: return c.booleanCellValue
            default: return c.stringCellValue
        }
    }

    protected void setValue(Cell cell, Object value) {
        switch (value) {
            case null: cell.setBlank(); break
            case Number: cell.setCellValue(((Number) value).doubleValue()); break
            case Boolean: cell.setCellValue((Boolean) value); break
            case Date: cell.setCellValue((Date) value); cell.cellStyle = dateStyle(); break
            case LocalDateTime: cell.setCellValue((LocalDateTime) value); cell.cellStyle = dateStyle(); break
            case LocalDate: cell.setCellValue((LocalDate) value); cell.cellStyle = dateStyle(); break
            default: cell.setCellValue(value.toString())
        }
    }

    private CellStyle dateStyle() {
        if (dateStyle == null) {
            dateStyle = workbook.createCellStyle()
            dateStyle.dataFormat = workbook.creationHelper.createDataFormat().getFormat('dd/mm/yyyy hh:mm')
        }
        dateStyle
    }

    XlsxExporter save(OutputStream out) {
        workbook.write(out)
        this
    }

    @Override
    void close() {
        workbook.close()
    }

    /** Formatação encadeada de uma célula (estilos novos por célula: bom para planilhas pequenas). */
    class CellFormatter {
        final Cell cell

        CellFormatter(Cell cell) { this.cell = cell }

        private CellStyle style() {
            CellStyle s = workbook.createCellStyle()
            s.cloneStyleFrom(cell.cellStyle)
            cell.cellStyle = s
            s
        }

        CellFormatter bold() {
            CellStyle s = style()
            Font f = workbook.createFont()
            f.bold = true
            s.font = f
            this
        }

        CellFormatter thickBorder() {
            CellStyle s = style()
            s.borderTop = s.borderBottom = s.borderLeft = s.borderRight = BorderStyle.THICK
            this
        }

        CellFormatter centre() { align(HorizontalAlignment.CENTER) }

        CellFormatter center() { align(HorizontalAlignment.CENTER) }

        CellFormatter left() { align(HorizontalAlignment.LEFT) }

        CellFormatter right() { align(HorizontalAlignment.RIGHT) }

        private CellFormatter align(HorizontalAlignment a) {
            style().alignment = a
            this
        }
    }
}
