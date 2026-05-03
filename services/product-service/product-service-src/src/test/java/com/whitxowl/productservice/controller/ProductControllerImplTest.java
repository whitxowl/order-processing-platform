package com.whitxowl.productservice.controller;

import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductControllerImplTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProductRepository productRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String ADMIN_ROLE   = "ROLE_ADMIN";
    private static final String USER_ROLE    = "ROLE_USER";

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    @Test
    void createProduct_asManager_shouldReturn201() {
        CreateProductRequest request = buildCreateRequest("Клавиатура", "keyboards", BigDecimal.valueOf(8990));

        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Клавиатура")
                .jsonPath("$.category").isEqualTo("keyboards")
                .jsonPath("$.id").isNotEmpty();
    }

    @Test
    void createProduct_withoutAuth_shouldReturn403() {
        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest("Клавиатура", "keyboards", BigDecimal.valueOf(8990)))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createProduct_asUser_shouldReturn403() {
        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest("Клавиатура", "keyboards", BigDecimal.valueOf(8990)))
                .header("X-User-Id", "user-1")
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void createProduct_invalidRequest_shouldReturn400() {
        webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new CreateProductRequest())
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors").isMap();
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    @Test
    void getProduct_visible_shouldReturn200WithoutAuth() {
        String id = createProductAsManager("Мышь", "mice", BigDecimal.valueOf(3990), true);

        webTestClient.get().uri("/api/v1/products/{id}", id)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id)
                .jsonPath("$.name").isEqualTo("Мышь");
    }

    @Test
    void getProduct_hidden_asAnonymous_shouldReturn404() {
        String id = createProductAsManager("Скрытый товар", "misc", BigDecimal.valueOf(100), false);

        webTestClient.get().uri("/api/v1/products/{id}", id)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getProduct_hidden_asManager_shouldReturn200() {
        String id = createProductAsManager("Скрытый товар", "misc", BigDecimal.valueOf(100), false);

        webTestClient.get().uri("/api/v1/products/{id}", id)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.visible").isEqualTo(false);
    }

    @Test
    void getProduct_notExists_shouldReturn404() {
        webTestClient.get().uri("/api/v1/products/{id}", "nonexistent-id")
                .exchange()
                .expectStatus().isNotFound();
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    @Test
    void updateProduct_asManager_shouldReturn200() {
        String id = createProductAsManager("Старое название", "keyboards", BigDecimal.valueOf(1000), true);

        webTestClient.put().uri("/api/v1/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Новое название", "price", 2000))
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Новое название")
                .jsonPath("$.price").isEqualTo(2000);
    }

    // ─── PUBLISH / HIDE ───────────────────────────────────────────────────────

    @Test
    void publishProduct_asManager_shouldSetVisibleTrue() {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), false);

        webTestClient.patch().uri("/api/v1/products/{id}/publish", id)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.visible").isEqualTo(true);
    }

    @Test
    void hideProduct_asManager_shouldSetVisibleFalse() {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), true);

        webTestClient.patch().uri("/api/v1/products/{id}/hide", id)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.visible").isEqualTo(false);
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Test
    void deleteProduct_asAdmin_shouldReturn204() {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), true);

        webTestClient.delete().uri("/api/v1/products/{id}", id)
                .header("X-User-Id", "admin-1")
                .header("X-User-Roles", ADMIN_ROLE)
                .exchange()
                .expectStatus().isNoContent();

        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void deleteProduct_asManager_shouldReturn403() {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), true);

        webTestClient.delete().uri("/api/v1/products/{id}", id)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ─── SEARCH ──────────────────────────────────────────────────────────────

    @Test
    void searchProducts_shouldReturnVisibleOnly() {
        createProductAsManager("Видимый", "keyboards", BigDecimal.valueOf(1000), true);
        createProductAsManager("Скрытый", "keyboards", BigDecimal.valueOf(2000), false);

        webTestClient.get().uri("/api/v1/products?category=keyboards")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].name").isEqualTo("Видимый");
    }

    @Test
    void searchProducts_asManager_shouldReturnAll() {
        createProductAsManager("Видимый", "keyboards", BigDecimal.valueOf(1000), true);
        createProductAsManager("Скрытый", "keyboards", BigDecimal.valueOf(2000), false);

        webTestClient.get().uri("/api/v1/products?category=keyboards")
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void searchProducts_withPriceFilter_shouldFilter() {
        createProductAsManager("Дешёвый", "mice", BigDecimal.valueOf(500), true);
        createProductAsManager("Дорогой", "mice", BigDecimal.valueOf(5000), true);

        webTestClient.get().uri("/api/v1/products?category=mice&maxPrice=1000")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].name").isEqualTo("Дешёвый");
    }

    // ─── IMAGES ──────────────────────────────────────────────────────────────

    @Test
    void uploadImage_asManager_shouldReturn200WithImageInResponse() {
        String id = createProductAsManager("Товар с фото", "misc", BigDecimal.valueOf(999), true);

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new ClassPathResource("/images/test-image.png"))
                .contentType(MediaType.IMAGE_PNG);

        String imageId = webTestClient.post().uri("/api/v1/products/{id}/images", id)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.images").isArray()
                .jsonPath("$.images[0].imageId").isNotEmpty()
                .jsonPath("$.images[0].primary").isEqualTo(true)
                .jsonPath("$.images[0].order").isEqualTo(0)
                .returnResult()
                .getResponseBody()
                .toString(); // используем для извлечения ниже

        // Отдельный запрос за содержимым изображения через extractImageId
        String actualImageId = extractImageIdFromBody(
                webTestClient.post().uri("/api/v1/products/{id}/images", id)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE)
                        .body(BodyInserters.fromMultipartData(multipart.build()))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(Map.class)
                        .returnResult()
                        .getResponseBody()
        );

        webTestClient.get().uri("/api/v1/products/images/{imageId}", actualImageId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_PNG);
    }

    @Test
    void uploadImage_withoutAuth_shouldReturn403() {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(999), true);

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new byte[100], MediaType.IMAGE_PNG).filename("test.png");

        webTestClient.post().uri("/api/v1/products/{id}/images", id)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void deleteImage_asManager_shouldRemoveFromProduct() {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(999), true);

        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("file", new ClassPathResource("/images/test-image.png"))
                .contentType(MediaType.IMAGE_PNG);

        String imageId = extractImageIdFromBody(
                webTestClient.post().uri("/api/v1/products/{id}/images", id)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE)
                        .body(BodyInserters.fromMultipartData(multipart.build()))
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody(Map.class)
                        .returnResult()
                        .getResponseBody()
        );

        webTestClient.delete().uri("/api/v1/products/{id}/images/{imageId}", id, imageId)
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.images").isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String createProductAsManager(String name, String category,
                                          BigDecimal price, boolean visible) {
        String id = webTestClient.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildCreateRequest(name, category, price))
                .header("X-User-Id", "manager-1")
                .header("X-User-Roles", MANAGER_ROLE)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id")
                .toString();

        if (visible) {
            webTestClient.patch().uri("/api/v1/products/{id}/publish", id)
                    .header("X-User-Id", "manager-1")
                    .header("X-User-Roles", MANAGER_ROLE)
                    .exchange()
                    .expectStatus().isOk();
        }
        return id;
    }

    private CreateProductRequest buildCreateRequest(String name, String category, BigDecimal price) {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setCategory(category);
        request.setPrice(price);
        return request;
    }

    @SuppressWarnings("unchecked")
    private String extractImageIdFromBody(Map<?, ?> body) {
        List<Map<String, Object>> images = (List<Map<String, Object>>) body.get("images");
        return images.get(0).get("imageId").toString();
    }
}