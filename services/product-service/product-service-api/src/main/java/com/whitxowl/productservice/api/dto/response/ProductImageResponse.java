package com.whitxowl.productservice.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private String imageId;
    private String url;
    private int order;
    private boolean primary;
}
