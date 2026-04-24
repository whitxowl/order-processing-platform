package com.whitxowl.productservice.controller;

import com.whitxowl.productservice.api.controller.ProductController;
import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.api.dto.request.UpdateProductRequest;
import com.whitxowl.productservice.api.dto.response.PageResponse;
import com.whitxowl.productservice.api.dto.response.ProductResponse;
import com.whitxowl.productservice.api.constant.RoleConstant;
import com.whitxowl.productservice.repository.ImageRepository;
import com.whitxowl.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
public class ProductControllerImpl implements ProductController {

    private final ProductService productService;
    private final ImageRepository imageRepository;

    @Override
    public ResponseEntity<ProductResponse> createProduct(CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<ProductResponse> getProduct(String id) {
        ProductResponse response = productService.getProduct(id, isManagerOrAdmin());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProductResponse> updateProduct(String id, UpdateProductRequest request) {
        ProductResponse response = productService.updateProduct(id, request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> deleteProduct(String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProductResponse> publishProduct(String id) {
        ProductResponse response = productService.publishProduct(id);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProductResponse> hideProduct(String id) {
        ProductResponse response = productService.hideProduct(id);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PageResponse<ProductResponse>> searchProducts(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String text,
            int page,
            int size) {
        PageResponse<ProductResponse> response = productService.searchProducts(
                category, minPrice, maxPrice, text, isManagerOrAdmin(), page, size
        );
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProductResponse> uploadImage(String id, MultipartFile file) {
        ProductResponse response = productService.uploadImage(id, file);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ProductResponse> deleteImage(String id, String imageId) {
        ProductResponse response = productService.deleteImage(id, imageId);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<byte[]> getImage(String imageId) {
        ImageRepository.ImageData imageData = imageRepository.load(imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageData.contentType()))
                .body(imageData.bytes());
    }

    private boolean isManagerOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(RoleConstant.ROLE_MANAGER)
                        || a.getAuthority().equals(RoleConstant.ROLE_ADMIN));
    }
}