package org.mapnaom.datalistbackend.common.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

public final class SearchField<T> {
    @FunctionalInterface
    public interface Value<T, V> {
        Expression<V> get(Root<T> root, CriteriaBuilder cb);
    }

    @FunctionalInterface
    private interface FilterBuilder<T> {
        Specification<T> build(SearchRequest.Filter filter);
    }

    private final String kind;
    private final FilterBuilder<T> builder;
    // A null sort expression explicitly disables sorting for this field.
    final Value<T, ?> sort;

    private SearchField(String kind, FilterBuilder<T> builder, Value<T, ?> sort) {
        this.kind = kind;
        this.builder = builder;
        this.sort = sort;
    }

    public static <T> SearchField<T> text(Value<T, String> display, Value<T, String> rawSort) {
        Value<T, String> sort = rawSort == null ? null : (root, cb) -> cb.nullif(rawSort.get(root, cb), "");
        return new SearchField<>("string", f -> SearchOperators.text(display, f), sort);
    }

    public static <T> SearchField<T> number(Value<T, BigDecimal> value, Value<T, ? extends Number> sort) {
        return new SearchField<>("number", f -> SearchOperators.number(value, f), sort);
    }

    public static <T> SearchField<T> date(Value<T, LocalDate> value, Value<T, LocalDate> sort) {
        return new SearchField<>("date", f -> SearchOperators.date(value, f), sort);
    }

    Specification<T> filter(SearchRequest.Filter filter) {
        if (!kind.equals(filter.kind()) || filter.operator() == null)
            throw new InvalidSearch("Invalid kind or missing operator for " + filter.key());
        return builder.build(filter);
    }
}
