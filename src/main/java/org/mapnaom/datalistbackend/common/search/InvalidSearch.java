package org.mapnaom.datalistbackend.common.search;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class InvalidSearch extends ResponseStatusException {
    InvalidSearch(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }
}
