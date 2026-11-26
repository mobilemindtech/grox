package org.grails.web.binding

import org.springframework.beans.propertyeditors.CustomDateEditor

import java.text.DateFormat

/** Compatibilidade com o {@code StructuredDateEditor} do Grails (o binding estruturado _day/_month/_year já é do Grox). */
class StructuredDateEditor extends CustomDateEditor {
    StructuredDateEditor(DateFormat dateFormat, boolean allowEmpty) {
        super(dateFormat, allowEmpty)
    }
}
