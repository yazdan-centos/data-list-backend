package org.mapnaom.datalistbackend.common.search;

import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

public record SearchRegistry<T>(Map<String, SearchField<T>> fields, String tieBreaker) {
    public SearchRegistry {
        fields = Map.copyOf(fields);
        if (!fields.containsKey(tieBreaker) || fields.get(tieBreaker).sort == null)
            throw new IllegalArgumentException("A sortable unique tie-breaker is required");
    }

    public record Search<T>(Specification<T> specification, Pageable pageable) {}
    private record Ordering<T>(SearchField<T> field, boolean ascending) {}

    public Search<T> prepare(SearchRequest request) {
        if (request.page < 0 || request.size < 1 || request.size > 100)
            throw new InvalidSearch("Expected page >= 0 and size between 1 and 100");
        List<Specification<T>> predicates = new ArrayList<>();
        request.filters.forEach((key, filter) -> {
            if (!key.equals(filter.key())) throw new InvalidSearch("Filter key mismatch for " + key);
            predicates.add(field(key).filter(filter));
        });

        List<Ordering<T>> ordering = new ArrayList<>();
        var seen = new HashSet<String>();
        for (String item : request.sort) {
            String[] parts = item.split(",", -1);
            if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc")))
                throw new InvalidSearch("Expected sort in field,asc or field,desc form");
            var field = field(parts[0]);
            if (field.sort == null || !seen.add(parts[0])) throw new InvalidSearch("Invalid sort: " + item);
            ordering.add(new Ordering<>(field, parts[1].equals("asc")));
        }
        if (!seen.contains(tieBreaker)) ordering.add(new Ordering<>(fields.get(tieBreaker), true));

        // Criteria orders support expressions and explicit NULLS LAST. Keep count queries unordered.
        predicates.add((root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class) {
                List<Order> orders = new ArrayList<>();
                for (var item : ordering) {
                    var raw = item.field.sort.get(root, cb);
                    orders.add(item.ascending ? cb.asc(raw, Nulls.LAST) : cb.desc(raw, Nulls.LAST));
                }
                query.orderBy(orders);
            }
            return null;
        });
        return new Search<>(Specification.allOf(predicates), PageRequest.of(request.page, request.size));
    }

    private SearchField<T> field(String key) {
        var field = fields.get(key);
        if (field == null) throw new InvalidSearch("Unknown field: " + key);
        return field;
    }
}
