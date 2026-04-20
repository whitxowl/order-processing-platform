package com.whitxowl.productservice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class ProductResponse {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private List<ProductImageResponse> images;
    private boolean visible;
    private Instant createdAt;
    private Instant updatedAt;
}
