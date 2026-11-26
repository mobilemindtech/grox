package org.springframework.beans.propertyeditors

import java.beans.PropertyEditorSupport
import java.text.DateFormat
import java.text.ParseException

/** Compatibilidade com o {@code CustomDateEditor} do Spring. */
class CustomDateEditor extends PropertyEditorSupport {
    final DateFormat dateFormat
    final boolean allowEmpty
    final int exactDateLength

    CustomDateEditor(DateFormat dateFormat, boolean allowEmpty, int exactDateLength = -1) {
        this.dateFormat = dateFormat
        this.allowEmpty = allowEmpty
        this.exactDateLength = exactDateLength
    }

    @Override
    void setAsText(String text) throws IllegalArgumentException {
        if (allowEmpty && !text?.trim()) {
            setValue(null)
            return
        }
        if (text != null && exactDateLength >= 0 && text.length() != exactDateLength) {
            throw new IllegalArgumentException("Data com tamanho diferente de ${exactDateLength}: ${text}")
        }
        try {
            setValue(dateFormat.parse(text))
        } catch (ParseException e) {
            throw new IllegalArgumentException("Data inválida: ${text}", e)
        }
    }

    @Override
    String getAsText() {
        Date d = (Date) getValue()
        d == null ? '' : dateFormat.format(d)
    }
}
