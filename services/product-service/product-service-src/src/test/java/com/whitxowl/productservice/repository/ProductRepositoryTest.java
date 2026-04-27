package com.whitxowl.productservice.repository;

import com.whitxowl.productservice.config.MongoConfig;
import com.whitxowl.productservice.config.TestConfig;
import com.whitxowl.productservice.domain.document.ProductDocument;
import com.whitxowl.productservice.domain.document.ProductImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Import({TestConfig.class, MongoConfig.class})
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void save_shouldPersistDocumentWithAuditDates() {
        ProductDocument saved = productRepository.save(
                buildProduct("Клавиатура", "keyboards", BigDecimal.valueOf(8990), true)
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Клавиатура");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnDocument_whenExists() {
        ProductDocument saved = productRepository.save(
                buildProduct("Мышь", "mice", BigDecimal.valueOf(3990), true)
        );

        Optional<ProductDocument> found = productRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Мышь");
        assertThat(found.get().getCategory()).isEqualTo("mice");
    }

    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        Optional<ProductDocument> found = productRepository.findById("000000000000000000000000");
        assertThat(found).isEmpty();
    }

    @Test
    void deleteById_shouldRemoveDocument() {
        ProductDocument saved = productRepository.save(
                buildProduct("Монитор", "monitors", BigDecimal.valueOf(25000), true)
        );

        productRepository.deleteById(saved.getId());

        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void findAll_shouldReturnAllDocuments() {
        productRepository.save(buildProduct("Товар 1", "cat1", BigDecimal.valueOf(100), true));
        productRepository.save(buildProduct("Товар 2", "cat2", BigDecimal.valueOf(200), false));

        assertThat(productRepository.findAll()).hasSize(2);
    }

    @Test
    void findAllReferencedImageIds_shouldReturnAllImageIds() {
        ProductImage img1 = ProductImage.builder().imageId("img-1").order(0).primary(true).build();
        ProductImage img2 = ProductImage.builder().imageId("img-2").order(1).primary(false).build();
        ProductImage img3 = ProductImage.builder().imageId("img-3").order(0).primary(true).build();

        ProductDocument doc1 = buildProduct("Товар 1", "cat", BigDecimal.valueOf(100), true);
        doc1.setImages(List.of(img1, img2));
        productRepository.save(doc1);

        ProductDocument doc2 = buildProduct("Товар 2", "cat", BigDecimal.valueOf(200), true);
        doc2.setImages(List.of(img3));
        productRepository.save(doc2);

        Set<String> imageIds = productRepository.findAllReferencedImageIds();

        assertThat(imageIds).containsExactlyInAnyOrder("img-1", "img-2", "img-3");
    }

    @Test
    void findAllReferencedImageIds_shouldReturnEmpty_whenNoImages() {
        productRepository.save(buildProduct("Товар", "cat", BigDecimal.valueOf(100), true));

        assertThat(productRepository.findAllReferencedImageIds()).isEmpty();
    }

    @Test
    void findByFilters_noFilters_shouldReturnOnlyVisible() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                null, null, null, null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent()).allMatch(ProductDocument::isVisible);
    }

    @Test
    void findByFilters_includeHidden_shouldReturnAll() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                null, null, null, null, true, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(5);
    }

    @Test
    void findByFilters_withCategory_shouldFilterByCategory() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                "keyboards", null, null, null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(p -> p.getCategory().equals("keyboards"));
    }

    @Test
    void findByFilters_withMaxPrice_shouldFilterByMaxPrice() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                null, null, BigDecimal.valueOf(3000), null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(p -> p.getPrice().doubleValue() <= 3000);
    }

    @Test
    void findByFilters_withMinPrice_shouldFilterByMinPrice() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                null, BigDecimal.valueOf(5000), null, null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Клавиатура игровая");
    }

    @Test
    void findByFilters_withPriceRange_shouldFilterByRange() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                null, BigDecimal.valueOf(1000), BigDecimal.valueOf(5000), null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .allMatch(p -> p.getPrice().doubleValue() >= 1000
                        && p.getPrice().doubleValue() <= 5000);
    }

    @Test
    void findByFilters_withCategoryAndMaxPrice_shouldCombineFilters() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                "mice", null, BigDecimal.valueOf(2000), null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Мышь офисная");
    }

    @Test
    void findByFilters_withPagination_shouldReturnCorrectPage() {
        saveFixtures();

        Page<ProductDocument> firstPage = productRepository.findByFilters(
                null, null, null, null, false, PageRequest.of(0, 2)
        );
        Page<ProductDocument> secondPage = productRepository.findByFilters(
                null, null, null, null, false, PageRequest.of(1, 2)
        );

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(secondPage.isLast()).isTrue();
    }

    @Test
    void findByFilters_noMatchingCategory_shouldReturnEmpty() {
        saveFixtures();

        Page<ProductDocument> result = productRepository.findByFilters(
                "nonexistent", null, null, null, false, PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void saveFixtures() {
        productRepository.save(buildProduct("Клавиатура игровая", "keyboards", BigDecimal.valueOf(8990), true));
        productRepository.save(buildProduct("Клавиатура офисная", "keyboards", BigDecimal.valueOf(2990), true));
        productRepository.save(buildProduct("Мышь игровая", "mice", BigDecimal.valueOf(4990), true));
        productRepository.save(buildProduct("Мышь офисная", "mice", BigDecimal.valueOf(990), true));
        productRepository.save(buildProduct("Скрытый товар", "keyboards", BigDecimal.valueOf(1000), false));
    }

    private ProductDocument buildProduct(String name, String category,
                                         BigDecimal price, boolean visible) {
        return ProductDocument.builder()
                .name(name)
                .category(category)
                .price(price)
                .visible(visible)
                .build();
    }
}