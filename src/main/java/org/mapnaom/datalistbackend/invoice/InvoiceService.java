package org.mapnaom.datalistbackend.invoice;

import java.util.List;
import org.mapnaom.datalistbackend.common.search.SearchPage;
import org.mapnaom.datalistbackend.common.search.SearchRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InvoiceService {
    private final InvoiceRepository repository;

    InvoiceService(InvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public SearchPage<InvoiceDto> search(SearchRequest request) {
        var search = InvoiceFields.REGISTRY.prepare(request);
        // JPA setFirstResult accepts an int. Short-circuit very distant empty pages.
        if (search.pageable().getOffset() > Integer.MAX_VALUE) {
            long count = repository.count(search.specification());
            if (search.pageable().getOffset() >= count) return new SearchPage<>(List.of(), count);
        }
        var page = repository.findAll(search.specification(), search.pageable());
        return new SearchPage<>(page.map(InvoiceDto::from).getContent(), page.getTotalElements());
    }
}
