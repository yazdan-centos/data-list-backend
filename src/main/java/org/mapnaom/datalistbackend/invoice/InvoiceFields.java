package org.mapnaom.datalistbackend.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.mapnaom.datalistbackend.common.search.SearchField;
import org.mapnaom.datalistbackend.common.search.SearchRegistry;

final class InvoiceFields {
    private static final SearchField.Value<Invoice, String> CUSTOMER = (r, cb) -> r.get("customer");
    private static final SearchField.Value<Invoice, BigDecimal> AMOUNT = (r, cb) -> r.get("amount");
    private static final SearchField.Value<Invoice, LocalDate> DATE = (r, cb) -> r.get("issuedAt");

    static final SearchRegistry<Invoice> REGISTRY = new SearchRegistry<>(Map.of(
            "id", SearchField.number((r, cb) -> cb.toBigDecimal(r.<Long>get("id")), (r, cb) -> r.get("id")),
            "customer", SearchField.text(CUSTOMER, CUSTOMER),
            "amount", SearchField.number(AMOUNT, AMOUNT),
            "issuedAt", SearchField.date(DATE, DATE),
            "status", SearchField.text((r, cb) -> cb.<Invoice.Status, String>selectCase(r.get("status"))
                    .when(Invoice.Status.paid, "پرداخت‌شده")
                    .when(Invoice.Status.pending, "در انتظار")
                    .when(Invoice.Status.overdue, "معوق")
                    .otherwise(""), (r, cb) -> r.get("status").cast(String.class))
    ), "id");

    private InvoiceFields() {}
}
