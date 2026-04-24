package com.whitxowl.productservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.api.dto.response.ProductResponse;
import com.whitxowl.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class ProductControllerImplTest {

    @Container
    static MongoDBContainer mongodb = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongodb::getReplicaSetUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String USER_ROLE = "ROLE_USER";

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    // ─── CREATE ──────────────────────────────────────────────────────────────

    @Test
    void createProduct_asManager_shouldReturn201() throws Exception {
        CreateProductRequest request = buildCreateRequest("Клавиатура", "keyboards", BigDecimal.valueOf(8990));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Клавиатура"))
                .andExpect(jsonPath("$.category").value("keyboards"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createProduct_withoutAuth_shouldReturn403() throws Exception {
        CreateProductRequest request = buildCreateRequest("Клавиатура", "keyboards", BigDecimal.valueOf(8990));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_asUser_shouldReturn403() throws Exception {
        CreateProductRequest request = buildCreateRequest("Клавиатура", "keyboards", BigDecimal.valueOf(8990));

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "user-1")
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isForbidden());
    }

    @Test
    void createProduct_invalidRequest_shouldReturn400() throws Exception {
        CreateProductRequest request = new CreateProductRequest();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isMap());
    }

    // ─── GET ─────────────────────────────────────────────────────────────────

    @Test
    void getProduct_visible_shouldReturn200WithoutAuth() throws Exception {
        String id = createProductAsManager("Мышь", "mice", BigDecimal.valueOf(3990), true);

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Мышь"));
    }

    @Test
    void getProduct_hidden_asAnonymous_shouldReturn404() throws Exception {
        String id = createProductAsManager("Скрытый товар", "misc", BigDecimal.valueOf(100), false);

        mockMvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getProduct_hidden_asManager_shouldReturn200() throws Exception {
        String id = createProductAsManager("Скрытый товар", "misc", BigDecimal.valueOf(100), false);

        mockMvc.perform(get("/api/v1/products/{id}", id)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));
    }

    @Test
    void getProduct_notExists_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", "nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    // ─── UPDATE ──────────────────────────────────────────────────────────────

    @Test
    void updateProduct_asManager_shouldReturn200() throws Exception {
        String id = createProductAsManager("Старое название", "keyboards", BigDecimal.valueOf(1000), true);

        String updateJson = """
                { "name": "Новое название", "price": 2000 }
                """;

        mockMvc.perform(put("/api/v1/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Новое название"))
                .andExpect(jsonPath("$.price").value(2000));
    }

    // ─── PUBLISH / HIDE ───────────────────────────────────────────────────────

    @Test
    void publishProduct_asManager_shouldSetVisibleTrue() throws Exception {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), false);

        mockMvc.perform(patch("/api/v1/products/{id}/publish", id)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true));
    }

    @Test
    void hideProduct_asManager_shouldSetVisibleFalse() throws Exception {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), true);

        mockMvc.perform(patch("/api/v1/products/{id}/hide", id)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(false));
    }

    // ─── DELETE ──────────────────────────────────────────────────────────────

    @Test
    void deleteProduct_asAdmin_shouldReturn204() throws Exception {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), true);

        mockMvc.perform(delete("/api/v1/products/{id}", id)
                        .header("X-User-Id", "admin-1")
                        .header("X-User-Roles", ADMIN_ROLE))
                .andExpect(status().isNoContent());

        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void deleteProduct_asManager_shouldReturn403() throws Exception {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(500), true);

        mockMvc.perform(delete("/api/v1/products/{id}", id)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isForbidden());
    }

    // ─── SEARCH ──────────────────────────────────────────────────────────────

    @Test
    void searchProducts_shouldReturnVisibleOnly() throws Exception {
        createProductAsManager("Видимый", "keyboards", BigDecimal.valueOf(1000), true);
        createProductAsManager("Скрытый", "keyboards", BigDecimal.valueOf(2000), false);

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "keyboards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Видимый"));
    }

    @Test
    void searchProducts_asManager_shouldReturnAll() throws Exception {
        createProductAsManager("Видимый", "keyboards", BigDecimal.valueOf(1000), true);
        createProductAsManager("Скрытый", "keyboards", BigDecimal.valueOf(2000), false);

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "keyboards")
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchProducts_withPriceFilter_shouldFilter() throws Exception {
        createProductAsManager("Дешёвый", "mice", BigDecimal.valueOf(500), true);
        createProductAsManager("Дорогой", "mice", BigDecimal.valueOf(5000), true);

        mockMvc.perform(get("/api/v1/products")
                        .param("category", "mice")
                        .param("maxPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Дешёвый"));
    }

    @Test
    void uploadImage_asManager_shouldReturn200WithImageInResponse() throws Exception {
        String id = createProductAsManager("Товар с фото", "misc", BigDecimal.valueOf(999), true);

        byte[] imageBytes = getClass().getResourceAsStream("/images/test-image.png").readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-image.png", "image/png", imageBytes
        );

        String json = mockMvc.perform(multipart("/api/v1/products/{id}/images", id)
                        .file(file)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isArray())
                .andExpect(jsonPath("$.images[0].imageId").isNotEmpty())
                .andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[0].order").value(0))
                .andReturn().getResponse().getContentAsString();

        String imageId = extractImageId(json);

        mockMvc.perform(get("/api/v1/products/images/{imageId}", imageId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void uploadImage_withoutAuth_shouldReturn403() throws Exception {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(999), true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", new byte[100]
        );

        mockMvc.perform(multipart("/api/v1/products/{id}/images", id)
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteImage_asManager_shouldRemoveFromProduct() throws Exception {
        String id = createProductAsManager("Товар", "misc", BigDecimal.valueOf(999), true);

        byte[] imageBytes = getClass().getResourceAsStream("/images/test-image.png").readAllBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-image.png", "image/png", imageBytes
        );

        String json = mockMvc.perform(multipart("/api/v1/products/{id}/images", id)
                        .file(file)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String imageId = extractImageId(json);

        mockMvc.perform(delete("/api/v1/products/{id}/images/{imageId}", id, imageId)
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images").isEmpty());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private String createProductAsManager(String name, String category,
                                          BigDecimal price, boolean visible) throws Exception {
        CreateProductRequest request = buildCreateRequest(name, category, price);

        String json = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("X-User-Id", "manager-1")
                        .header("X-User-Roles", MANAGER_ROLE))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = objectMapper.readValue(json, Map.class).get("id").toString();

        if (visible) {
            mockMvc.perform(patch("/api/v1/products/{id}/publish", id)
                    .header("X-User-Id", "manager-1")
                    .header("X-User-Roles", MANAGER_ROLE));
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
    private String extractImageId(String json) throws Exception {
        List<Map<String, Object>> images = (List<Map<String, Object>>)
                objectMapper.readValue(json, Map.class).get("images");
        return images.get(0).get("imageId").toString();
    }
}
