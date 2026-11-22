package org.mapnaom.datalistbackend.common.search;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

public final class SearchRequest {
    @JsonSetter(nulls = Nulls.FAIL)
    public int page = 0;
    @JsonSetter(nulls = Nulls.FAIL)
    public int size = 5;
    @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
    public List<String> sort = List.of();
    @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
    public Map<String, Filter> filters = Map.of();

    public record Filter(String key, String kind, String operator, JsonNode value, JsonNode valueTo) {}
}
