package org.mapnaom.datalistbackend.invoice;

import org.mapnaom.datalistbackend.common.search.SearchPage;
import org.mapnaom.datalistbackend.common.search.SearchRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invoices")
class InvoiceController {
    private final InvoiceService service;

    InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @PostMapping(value = "/search", consumes = "application/json")
    SearchPage<InvoiceDto> search(@RequestBody SearchRequest request) {
        return service.search(request);
    }
}
