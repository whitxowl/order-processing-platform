package com.whitxowl.productservice.repository;

import com.whitxowl.productservice.domain.document.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductRepositoryCustom {

    Page<ProductDocument> findByFilters(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String text,
            boolean includeHidden,
            Pageable pageable
    );
}