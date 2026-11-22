package org.mapnaom.datalistbackend.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
class Invoice {
    @Id
    Long id;
    @Column(nullable = false)
    String customer;
    @Column(nullable = false, columnDefinition = "numeric")
    BigDecimal amount;
    @Column(nullable = false)
    LocalDate issuedAt;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    Status status;

    enum Status { paid, pending, overdue }
}
