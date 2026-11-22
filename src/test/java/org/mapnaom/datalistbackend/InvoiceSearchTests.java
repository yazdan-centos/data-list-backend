package org.mapnaom.datalistbackend;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql(statements = "delete from invoice")
@Sql("/dev-invoices.sql")
class InvoiceSearchTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    MockMvc mvc;

    @BeforeEach
    void init() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private JsonNode postSearch(String body, int expectedStatus) throws Exception {
        var response = mvc.perform(post("/api/invoices/search").contentType("application/json")
                        .content(body.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }

    private void page(Object request, int total, Integer... ids) throws Exception {
        var response = postSearch(json.writeValueAsString(request), 200);
        assertThat(response.get("totalElements").intValue()).isEqualTo(total);
        assertThat(response.get("content").valueStream().map(row -> row.get("id").intValue()).toList())
                .containsExactly(ids);
    }

    private Map<String, Object> filter(String key, String kind, String operator, Object value) {
        return Map.of("key", key, "kind", kind, "operator", operator, "value", value);
    }

    @Test
    void pagesCountAllMatchesAndUseIdForTies() throws Exception {
        page(Map.of(), 12, 1001, 1002, 1003, 1004, 1005);
        var filters = Map.of("status", filter("status", "string", "equals", "در انتظار"),
                "amount", Map.of("key", "amount", "kind", "number", "operator", "between",
                        "value", 19900000, "valueTo", 3100000));
        page(Map.of("filters", filters, "sort", List.of("amount,desc"), "page", 1, "size", 2),
                4, 1002, 1009);
        page(Map.of("filters", filters, "page", 4), 4);
        page(Map.of("page", Integer.MAX_VALUE), 12);
        page(Map.of("sort", List.of("status,asc"), "page", 1, "size", 3), 12, 1001, 1004, 1006);
        page(Map.of("sort", List.of("status,asc", "id,desc"), "size", 3), 12, 1011, 1007, 1003);
        var row = postSearch("{}", 200).get("content").get(0);
        assertThat(row.get("amount").isNumber()).isTrue();
        assertThat(row.get("id").isNumber()).isTrue();
        assertThat(row.get("issuedAt").stringValue()).isEqualTo("2024-03-20");
        assertThat(row.get("status").stringValue()).isEqualTo("paid");
    }

    @Test
    void dateBoundsAreInclusiveAndSingleComparisonsStrict() throws Exception {
        var range = Map.of("key", "issuedAt", "kind", "date", "operator", "between",
                "value", "2024-04-18", "valueTo", "2024-03-20");
        page(Map.of("filters", Map.of("issuedAt", range)), 3, 1001, 1002, 1003);
        page(Map.of("filters", Map.of("issuedAt", filter("issuedAt", "date", "before", "2024-04-02"))), 1, 1001);
        page(Map.of("filters", Map.of("issuedAt", filter("issuedAt", "date", "after", "2024-09-05"))), 1, 1012);
    }

    @Test
    void incompleteFiltersPassThrough() throws Exception {
        var filters = Map.of("amount", filter("amount", "number", "between", 42),
                "issuedAt", Map.of("kind", "date", "key", "issuedAt", "operator", "equals"),
                "customer", filter("customer", "string", "contains", " \t"));
        page(Map.of("filters", filters, "size", 1), 12, 1001);
    }

    @Test
    void textNormalizationAndLikeEscaping() throws Exception {
        jdbc.update("update invoice set customer = ? where id = 1001", "\tكـِي۱۲٣ %_! A ");
        jdbc.update("update invoice set customer = ? where id = 1002", "کی123 xy! a");
        for (String op : List.of("startsWith", "contains", "endsWith", "equals")) {
            var filters = Map.of("customer", filter("customer", "string", op, " کی123 %_! a "));
            page(Map.of("filters", filters), 1, 1001);
        }
        page(Map.of("filters", Map.of("customer", filter("customer", "string", "doesNotContain", "%_!")),
                "size", 1), 11, 1002);
        page(Map.of("filters", Map.of("status", filter("status", "string", "endsWith", "شده"))),
                5, 1001, 1004, 1006, 1008, 1010);
    }

    @Test
    void nullOperandsInDatabaseAndEmptySortValues() throws Exception {
        // Exercise nullable expressions in the shared layer. PostgreSQL rolls back this fixture's DDL.
        for (String column : List.of("amount", "customer", "issued_at"))
            jdbc.execute("alter table invoice alter column " + column + " drop not null");
        jdbc.update("update invoice set amount=null, customer=null, issued_at=null where id=1001");
        jdbc.update("update invoice set customer='' where id=1002");
        page(Map.of("filters", Map.of("amount", filter("amount", "number", "notEquals", 4300000)),
                "size", 1), 11, 1001);
        var outside = Map.of("key", "amount", "kind", "number", "operator", "notBetween",
                "value", 98000000, "valueTo", 640000);
        page(Map.of("filters", Map.of("amount", outside)), 1, 1001);
        page(Map.of("filters", Map.of("issuedAt", filter("issuedAt", "date", "before", "2024-04-18"))), 1, 1002);
        page(Map.of("filters", Map.of("customer", Map.of("key","customer","kind","string","operator","isEmpty"))),
                2, 1001, 1002);
        for (String dir : List.of("asc", "desc"))
            page(Map.of("sort", List.of("customer," + dir), "page", 5, "size", 2), 12, 1001, 1002);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"page\":-1}", "{\"size\":0}", "{\"size\":101}", "{\"page\":\"0\"}", "{\"size\":1.5}",
            "{\"sort\":[\"amount,up\"]}", "{\"sort\":[\"amount\"]}", "{\"sort\":[\"customer.name,asc\"]}",
            "{\"unknown\":0}", "{\"filters\":{\"other\":{\"key\":\"other\",\"kind\":\"number\",\"operator\":\"equals\"}}}",
            "{\"filters\":{\"id\":{\"key\":\"amount\",\"kind\":\"number\",\"operator\":\"equals\"}}}",
            "{\"filters\":{\"amount\":{\"key\":\"amount\",\"kind\":\"string\",\"operator\":\"equals\"}}}",
            "{\"filters\":{\"amount\":{\"key\":\"amount\",\"kind\":\"number\",\"operator\":\"contains\"}}}",
            "{\"filters\":{\"amount\":{\"key\":\"amount\",\"kind\":\"number\",\"operator\":\"between\",\"valueTo\":\"2\"}}}",
            "{\"filters\":{\"issuedAt\":{\"key\":\"issuedAt\",\"kind\":\"date\",\"operator\":\"between\",\"valueTo\":\"2023-02-29\"}}}",
            "{\"filters\":{\"issuedAt\":{\"key\":\"issuedAt\",\"kind\":\"date\",\"operator\":\"equals\",\"value\":\"\"}}}",
            "{\"filters\":{\"customer\":{\"key\":\"customer\",\"kind\":\"string\",\"operator\":\"isEmpty\",\"value\":10}}}"
    })
    void rejectsMalformedRequests(String body) throws Exception {
        var problem = postSearch(body, 400);
        assertThat(problem.get("status").intValue()).isEqualTo(400);
        assertThat(problem.get("detail").isString()).isTrue();
    }
}
