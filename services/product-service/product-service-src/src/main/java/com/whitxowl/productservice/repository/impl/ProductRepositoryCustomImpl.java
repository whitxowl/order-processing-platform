package com.whitxowl.productservice.repository.impl;

import com.whitxowl.productservice.domain.document.ProductDocument;
import com.whitxowl.productservice.repository.ProductRepositoryCustom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<ProductDocument> findByFilters(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String text,
            boolean includeHidden,
            Pageable pageable
    ) {
        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        if (!includeHidden) {
            criteriaList.add(Criteria.where("visible").is(true));
        }

        if (category != null && !category.isBlank()) {
            criteriaList.add(Criteria.where("category").is(category));
        }

        if (minPrice != null && maxPrice != null) {
            criteriaList.add(Criteria.where("price")
                    .gte(minPrice.doubleValue())
                    .lte(maxPrice.doubleValue()));
        } else if (minPrice != null) {
            criteriaList.add(Criteria.where("price").gte(minPrice.doubleValue()));
        } else if (maxPrice != null) {
            criteriaList.add(Criteria.where("price").lte(maxPrice.doubleValue()));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(new Criteria[0])));
        }

        if (text != null && !text.isBlank()) {
            query.addCriteria(TextCriteria.forDefaultLanguage().matchingPhrase(text));
        }

        long total = mongoTemplate.count(query, ProductDocument.class);

        query.with(pageable);
        List<ProductDocument> results = mongoTemplate.find(query, ProductDocument.class);

        return new PageImpl<>(results, pageable, total);
    }
}