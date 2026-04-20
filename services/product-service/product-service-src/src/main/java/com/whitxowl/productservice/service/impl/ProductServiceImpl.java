package com.whitxowl.productservice.service.impl;

import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.api.dto.request.UpdateProductRequest;
import com.whitxowl.productservice.api.dto.response.PageResponse;
import com.whitxowl.productservice.api.dto.response.ProductResponse;
import com.whitxowl.productservice.domain.document.ProductDocument;
import com.whitxowl.productservice.domain.document.ProductImage;
import com.whitxowl.productservice.exception.ImageNotFoundException;
import com.whitxowl.productservice.exception.ProductNotFoundException;
import com.whitxowl.productservice.mapper.ProductMapper;
import com.whitxowl.productservice.repository.ImageRepository;
import com.whitxowl.productservice.repository.ProductRepository;
import com.whitxowl.productservice.service.ProductService;
import com.whitxowl.productservice.service.validation.ImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final ProductMapper productMapper;
    private final ImageValidator imageValidator;

    @Override
    public ProductResponse createProduct(CreateProductRequest request) {
        ProductDocument document = productMapper.toDocument(request);
        ProductDocument saved = productRepository.save(document);
        log.info("Created product: id={}, name={}", saved.getId(), saved.getName());
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse getProduct(String id, boolean includeHidden) {
        ProductDocument document = findById(id);
        if (!includeHidden && !document.isVisible()) {
            throw new ProductNotFoundException(id);
        }
        return productMapper.toResponse(document);
    }

    @Override
    public ProductResponse updateProduct(String id, UpdateProductRequest request) {
        ProductDocument document = findById(id);
        productMapper.updateDocument(request, document);
        ProductDocument saved = productRepository.save(document);
        log.info("Updated product: id={}", id);
        return productMapper.toResponse(saved);
    }

    @Override
    public void deleteProduct(String id) {
        ProductDocument document = findById(id);

        List<String> imageIds = document.getImages().stream()
                .map(ProductImage::getImageId)
                .toList();

        productRepository.deleteById(id);

        imageIds.forEach(imageRepository::delete);

        log.info("Deleted product: id={}", id);
    }

    @Override
    public ProductResponse publishProduct(String id) {
        ProductDocument document = findById(id);
        document.setVisible(true);
        ProductDocument saved = productRepository.save(document);
        log.info("Published product: id={}", id);
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse hideProduct(String id) {
        ProductDocument document = findById(id);
        document.setVisible(false);
        ProductDocument saved = productRepository.save(document);
        log.info("Hidden product: id={}", id);
        return productMapper.toResponse(saved);
    }

    @Override
    public PageResponse<ProductResponse> searchProducts(
            String category,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String text,
            boolean includeHidden,
            int page,
            int size
    ) {
        Page<ProductDocument> result = productRepository.findByFilters(
                category, minPrice, maxPrice, text, includeHidden,
                PageRequest.of(page, size)
        );

        List<ProductResponse> content = result.getContent().stream()
                .map(productMapper::toResponse)
                .toList();

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Override
    public ProductResponse uploadImage(String id, MultipartFile file) {
        imageValidator.validate(file);

        ProductDocument document = findById(id);

        String imageId = imageRepository.store(file);

        boolean isFirst = document.getImages().isEmpty();
        int order = document.getImages().size();

        ProductImage image = ProductImage.builder()
                .imageId(imageId)
                .order(order)
                .primary(isFirst)
                .build();

        document.getImages().add(image);
        ProductDocument saved = productRepository.save(document);

        log.info("Uploaded image: productId={}, imageId={}, primary={}", id, imageId, isFirst);
        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponse deleteImage(String productId, String imageId) {
        ProductDocument document = findById(productId);

        boolean exists = document.getImages().stream()
                .anyMatch(img -> img.getImageId().equals(imageId));

        if (!exists) {
            throw new ImageNotFoundException(imageId);
        }

        document.getImages().removeIf(img -> img.getImageId().equals(imageId));
        reorderImages(document);
        productRepository.save(document);

        imageRepository.delete(imageId);

        log.info("Deleted image: productId={}, imageId={}", productId, imageId);
        return productMapper.toResponse(document);
    }

    private ProductDocument findById(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void reorderImages(ProductDocument document) {
        List<ProductImage> images = document.getImages();

        for (int i = 0; i < images.size(); i++) {
            images.get(i).setOrder(i);
        }

        boolean hasPrimary = images.stream().anyMatch(ProductImage::isPrimary);
        if (!hasPrimary && !images.isEmpty()) {
            images.get(0).setPrimary(true);
        }
    }
}