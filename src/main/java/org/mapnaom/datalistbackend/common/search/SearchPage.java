package org.mapnaom.datalistbackend.common.search;

import java.util.List;

public record SearchPage<T>(List<T> content, long totalElements) {}
