package com.whitxowl.productservice.service;

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
import com.whitxowl.productservice.service.impl.ProductServiceImpl;
import com.whitxowl.productservice.service.validation.ImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ImageRepository imageRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ImageValidator imageValidator;

    @InjectMocks
    private ProductServiceImpl productService;

    private ProductDocument document;
    private ProductResponse response;

    @BeforeEach
    void setUp() {
        document = ProductDocument.builder()
                .id("product-1")
                .name("Клавиатура")
                .price(BigDecimal.valueOf(8990))
                .category("keyboards")
                .visible(true)
                .images(new ArrayList<>())
                .build();

        response = ProductResponse.builder()
                .id("product-1")
                .name("Клавиатура")
                .price(BigDecimal.valueOf(8990))
                .category("keyboards")
                .visible(true)
                .images(List.of())
                .build();
    }

    @Test
    void createProduct_shouldSaveAndReturnResponse() {
        CreateProductRequest request = new CreateProductRequest();
        when(productMapper.toDocument(request)).thenReturn(document);
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        ProductResponse result = productService.createProduct(request);

        assertThat(result).isEqualTo(response);
        verify(productRepository).save(document);
    }

    @Test
    void getProduct_visible_shouldReturnResponse() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(productMapper.toResponse(document)).thenReturn(response);

        ProductResponse result = productService.getProduct("product-1", false);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getProduct_hiddenAndIncludeHidden_shouldReturnResponse() {
        document.setVisible(false);
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(productMapper.toResponse(document)).thenReturn(response);

        ProductResponse result = productService.getProduct("product-1", true);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getProduct_hiddenAndNotIncludeHidden_shouldThrowNotFound() {
        document.setVisible(false);
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> productService.getProduct("product-1", false))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProduct_notExists_shouldThrowNotFound() {
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct("missing", false))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void updateProduct_shouldUpdateAndReturnResponse() {
        UpdateProductRequest request = new UpdateProductRequest();
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        ProductResponse result = productService.updateProduct("product-1", request);

        assertThat(result).isEqualTo(response);
        verify(productMapper).updateDocument(request, document);
        verify(productRepository).save(document);
    }

    @Test
    void deleteProduct_shouldDeleteDocumentAndImages() {
        ProductImage image = ProductImage.builder().imageId("img-1").build();
        document.getImages().add(image);
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));

        productService.deleteProduct("product-1");

        verify(productRepository).deleteById("product-1");
        verify(imageRepository).delete("img-1");
    }

    @Test
    void deleteProduct_noImages_shouldDeleteDocument() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));

        productService.deleteProduct("product-1");

        verify(productRepository).deleteById("product-1");
        verify(imageRepository, never()).delete(any());
    }

    @Test
    void publishProduct_shouldSetVisibleTrueAndSave() {
        document.setVisible(false);
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        productService.publishProduct("product-1");

        assertThat(document.isVisible()).isTrue();
        verify(productRepository).save(document);
    }

    @Test
    void hideProduct_shouldSetVisibleFalseAndSave() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        productService.hideProduct("product-1");

        assertThat(document.isVisible()).isFalse();
        verify(productRepository).save(document);
    }

    @Test
    void searchProducts_shouldReturnPageResponse() {
        var page = new PageImpl<>(List.of(document), PageRequest.of(0, 20), 1);
        when(productRepository.findByFilters(null, null, null, null, false, PageRequest.of(0, 20)))
                .thenReturn(page);
        when(productMapper.toResponse(document)).thenReturn(response);

        PageResponse<ProductResponse> result = productService.searchProducts(
                null, null, null, null, false, 0, 20
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void uploadImage_firstImage_shouldBecomePrimary() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]
        );
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(imageRepository.store(file)).thenReturn("img-new");
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        productService.uploadImage("product-1", file);

        verify(imageValidator).validate(file);
        assertThat(document.getImages()).hasSize(1);
        assertThat(document.getImages().get(0).isPrimary()).isTrue();
        assertThat(document.getImages().get(0).getOrder()).isZero();
    }

    @Test
    void uploadImage_secondImage_shouldNotBePrimary() {
        ProductImage existing = ProductImage.builder()
                .imageId("img-1").order(0).primary(true).build();
        document.getImages().add(existing);

        MockMultipartFile file = new MockMultipartFile(
                "file", "photo2.jpg", "image/jpeg", new byte[100]
        );
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(imageRepository.store(file)).thenReturn("img-2");
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        productService.uploadImage("product-1", file);

        assertThat(document.getImages()).hasSize(2);
        assertThat(document.getImages().get(1).isPrimary()).isFalse();
        assertThat(document.getImages().get(1).getOrder()).isEqualTo(1);
    }

    @Test
    void deleteImage_shouldRemoveImageAndReorder() {
        ProductImage img1 = ProductImage.builder().imageId("img-1").order(0).primary(true).build();
        ProductImage img2 = ProductImage.builder().imageId("img-2").order(1).primary(false).build();
        document.getImages().add(img1);
        document.getImages().add(img2);

        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));
        when(productRepository.save(document)).thenReturn(document);
        when(productMapper.toResponse(document)).thenReturn(response);

        productService.deleteImage("product-1", "img-1");

        verify(imageRepository).delete("img-1");
        assertThat(document.getImages()).hasSize(1);
        assertThat(document.getImages().get(0).getImageId()).isEqualTo("img-2");
        assertThat(document.getImages().get(0).getOrder()).isZero();
        assertThat(document.getImages().get(0).isPrimary()).isTrue();
    }

    @Test
    void deleteImage_notExists_shouldThrowImageNotFound() {
        when(productRepository.findById("product-1")).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> productService.deleteImage("product-1", "missing-img"))
                .isInstanceOf(ImageNotFoundException.class);

        verify(imageRepository, never()).delete(any());
        verify(productRepository, never()).save(any());
    }
}