package org.mapnaom.datalistbackend.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists sample invoices at startup so the table has data to page, sort and filter.
 *
 * <p>Safe to run on every start: rows are keyed by their fixed id, and any id that already
 * exists is left untouched, so edits made to seeded rows are never overwritten.
 * Disable with {@code app.seed.invoices.enabled=false}.
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.seed.invoices.enabled", havingValue = "true", matchIfMissing = true)
class InvoiceDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InvoiceDataInitializer.class);

    private final InvoiceRepository repository;

    InvoiceDataInitializer(InvoiceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Invoice> samples = samples();
        Set<Long> existing = repository.findAllById(samples.stream().map(row -> row.id).toList())
                .stream().map(row -> row.id).collect(Collectors.toSet());
        List<Invoice> missing = samples.stream().filter(row -> !existing.contains(row.id)).toList();

        if (missing.isEmpty()) {
            log.info("Invoice seed skipped: all {} sample invoices already exist", samples.size());
            return;
        }
        repository.saveAll(missing);
        log.info("Invoice seed inserted {} of {} sample invoices", missing.size(), samples.size());
    }

    static List<Invoice> samples() {
        return List.of(
                invoice(1001, "علی رضایی", "12500000", "2024-03-20", Invoice.Status.paid),
                invoice(1002, "زهرا احمدی", "4300000", "2024-04-02", Invoice.Status.pending),
                invoice(1003, "پویا کریمی", "98000000", "2024-04-18", Invoice.Status.overdue),
                invoice(1004, "آیدا نوری", "760000", "2024-05-01", Invoice.Status.paid),
                invoice(1005, "یاسر ابراهیمی", "15200000", "2024-05-14", Invoice.Status.pending),
                invoice(1006, "ستاره قاسمی", "2250000", "2024-06-03", Invoice.Status.paid),
                invoice(1007, "بهرام شریفی", "54000000", "2024-06-21", Invoice.Status.overdue),
                invoice(1008, "ژیلا حسینی", "8800000", "2024-07-09", Invoice.Status.paid),
                invoice(1009, "کامران اکبری", "3100000", "2024-07-30", Invoice.Status.pending),
                invoice(1010, "گلناز فرهادی", "27500000", "2024-08-12", Invoice.Status.paid),
                invoice(1011, "ایمان یوسفی", "640000", "2024-09-05", Invoice.Status.overdue),
                invoice(1012, "ملیکا صادقی", "19900000", "2024-09-27", Invoice.Status.pending));
    }

    private static Invoice invoice(long id, String customer, String amount, String issuedAt,
                                   Invoice.Status status) {
        Invoice row = new Invoice();
        row.id = id;
        row.customer = customer;
        row.amount = new BigDecimal(amount);
        row.issuedAt = LocalDate.parse(issuedAt);
        row.status = status;
        return row;
    }
}
