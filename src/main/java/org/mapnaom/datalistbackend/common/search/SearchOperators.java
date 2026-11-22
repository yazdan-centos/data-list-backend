package org.mapnaom.datalistbackend.common.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.JsonNode;

final class SearchOperators {
    private static final Set<String> TEXT = Set.of("contains", "doesNotContain", "startsWith",
            "endsWith", "equals", "isEmpty", "isNotEmpty");
    private static final Set<String> NUMBER = Set.of("equals", "notEquals", "greaterThan",
            "greaterThanOrEqual", "lessThan", "lessThanOrEqual", "between", "notBetween");
    private static final Set<String> DATE = Set.of("equals", "before", "after", "between");
    // ECMAScript WhiteSpace and LineTerminator characters used by String.trim().
    private static final String SPACE = " \t\n\r\f\u000b\u00a0\u1680\u2000\u2001\u2002\u2003\u2004"
            + "\u2005\u2006\u2007\u2008\u2009\u200a\u2028\u2029\u202f\u205f\u3000\ufeff";
    private static final String FROM = "۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩يك٫٬،−ـ"
            + "ًٌٍَُِّْٕٖٜٟٓٔٗ٘ٙٚٛٝٞ";
    private static final String TO = "01234567890123456789یک.,,-";

    private SearchOperators() {}

    private static Expression<String> normalize(CriteriaBuilder cb, Expression<String> value) {
        var translated = cb.function("translate", String.class, cb.coalesce(value, ""),
                cb.literal(FROM), cb.literal(TO));
        return cb.function("btrim", String.class, cb.lower(translated), cb.literal(SPACE));
    }

    static <T> Specification<T> text(SearchField.Value<T, String> field, SearchRequest.Filter f) {
        checkOperator(f, TEXT);
        if (f.valueTo() != null && !f.valueTo().isMissingNode())
            throw new InvalidSearch("Unexpected valueTo for " + f.key());
        JsonNode node = f.value();
        if (node != null && !node.isMissingNode() && !node.isString())
            throw new InvalidSearch("Expected text for " + f.key());
        String input = node == null ? "" : node.stringValue("");
        boolean unary = Set.of("isEmpty", "isNotEmpty").contains(f.operator());
        if (!unary && input.chars().allMatch(c -> SPACE.indexOf(c) >= 0))
            return Specification.unrestricted();
        return (root, query, cb) -> {
            var text = normalize(cb, field.get(root, cb));
            if (f.operator().equals("isEmpty")) return cb.equal(text, "");
            if (f.operator().equals("isNotEmpty")) return cb.notEqual(text, "");
            if (f.operator().equals("equals")) return cb.equal(text, normalize(cb, cb.literal(input)));
            String escaped = input.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            Expression<String> pattern = normalize(cb, cb.literal(escaped));
            if (!f.operator().equals("startsWith")) pattern = cb.concat("%", pattern);
            if (!f.operator().equals("endsWith")) pattern = cb.concat(pattern, "%");
            Predicate matches = cb.like(text, pattern, '!');
            return f.operator().equals("doesNotContain") ? cb.not(matches) : matches;
        };
    }

    private static void checkOperator(SearchRequest.Filter f, Set<String> allowed) {
        if (!allowed.contains(f.operator())) throw new InvalidSearch("Invalid operator for " + f.key());
    }

    private static boolean absent(JsonNode n) {
        return n == null || n.isNull() || n.isMissingNode();
    }

    private static BigDecimal decimal(JsonNode n, String key) {
        if (absent(n)) return null;
        if (!n.isNumber()) throw new InvalidSearch("Expected a JSON number for " + key);
        return n.decimalValue();
    }

    private static LocalDate day(JsonNode n, String key) {
        if (absent(n)) return null;
        if (n.isString() && n.stringValue().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            try {
                LocalDate parsed = LocalDate.parse(n.stringValue());
                if (parsed.getYear() > 0) return parsed;
            } catch (DateTimeParseException ignored) {
                // Report impossible dates using the same field-level validation error.
            }
        }
        throw new InvalidSearch("Expected a Gregorian YYYY-MM-DD date for " + key);
    }

    static <T> Specification<T> number(SearchField.Value<T, BigDecimal> field, SearchRequest.Filter f) {
        checkOperator(f, NUMBER);
        return comparison(field, f.operator(), decimal(f.value(), f.key()), decimal(f.valueTo(), f.key()));
    }

    static <T> Specification<T> date(SearchField.Value<T, LocalDate> field, SearchRequest.Filter f) {
        checkOperator(f, DATE);
        return comparison(field, f.operator(), day(f.value(), f.key()), day(f.valueTo(), f.key()));
    }

    private static <T, V extends Comparable<? super V>> Specification<T> comparison(
            SearchField.Value<T, V> field, String op, V a, V b) {
        boolean range = op.equals("between") || op.equals("notBetween");
        if (a == null || range && b == null) return Specification.unrestricted();
        return (root, query, cb) -> {
            var value = field.get(root, cb);
            return switch (op) {
                case "equals" -> cb.equal(value, a);
                case "notEquals" -> cb.or(value.isNull(), cb.notEqual(value, a));
                case "greaterThan", "after" -> cb.greaterThan(value, a);
                case "greaterThanOrEqual" -> cb.greaterThanOrEqualTo(value, a);
                case "lessThan", "before" -> cb.lessThan(value, a);
                case "lessThanOrEqual" -> cb.lessThanOrEqualTo(value, a);
                case "between", "notBetween" -> {
                    boolean forward = a.compareTo(b) <= 0;
                    var inside = cb.between(value, forward ? a : b, forward ? b : a);
                    yield op.equals("between") ? inside : cb.or(value.isNull(), cb.not(inside));
                }
                default -> throw new IllegalStateException(op);
            };
        };
    }
}
