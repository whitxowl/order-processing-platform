package com.whitxowl.productservice.service;

import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.api.dto.request.UpdateProductRequest;
import com.whitxowl.productservice.api.dto.response.PageResponse;
import com.whitxowl.productservice.api.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse getProduct(String id, boolean includeHidden);

    ProductResponse updateProduct(String id, UpdateProductRequest request);

    void deleteProduct(String id);

    ProductResponse publishProduct(String id);

    ProductResponse hideProduct(String id);

    PageResponse<ProductResponse> searchProducts(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String text,
            boolean includeHidden,
            int page,
            int size
    );

    ProductResponse uploadImage(String id, MultipartFile file);

    ProductResponse deleteImage(String productId, String imageId);
}