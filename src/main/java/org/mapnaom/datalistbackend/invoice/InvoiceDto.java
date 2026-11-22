package org.mapnaom.datalistbackend.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

record InvoiceDto(long id, String customer, BigDecimal amount, LocalDate issuedAt, String status) {
    static InvoiceDto from(Invoice row) {
        return new InvoiceDto(row.id, row.customer, row.amount, row.issuedAt, row.status.name());
    }
}
