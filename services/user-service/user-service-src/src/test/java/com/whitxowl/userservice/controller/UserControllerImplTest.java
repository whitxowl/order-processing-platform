package com.whitxowl.userservice.controller;

import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;
import com.whitxowl.userservice.config.TestConfig;
import com.whitxowl.userservice.exception.UserNotFoundException;
import com.whitxowl.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
@Testcontainers
class UserControllerImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private UserService userService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String BASE_URL   = "/api/v1/users";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String USER_ROLE  = "ROLE_USER";

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void getUser_shouldReturn200_whenAuthenticated() {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenReturn(UserResponse.builder()
                .id(id).email("user@example.com").roles(Set.of(USER_ROLE)).build());

        webTestClient.get().uri(BASE_URL + "/" + id)
                .header("X-User-Id", id.toString())
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("user@example.com");
    }

    @Test
    void getUser_shouldReturn401_whenNoHeaders() {
        webTestClient.get().uri(BASE_URL + "/" + UUID.randomUUID())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getUser_shouldReturn404_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenThrow(new UserNotFoundException(id));

        webTestClient.get().uri(BASE_URL + "/" + id)
                .header("X-User-Id", id.toString())
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── addRole ───────────────────────────────────────────────────────────────

    @Test
    void addRole_asAdmin_shouldReturn200() {
        UUID id = UUID.randomUUID();
        UserResponse response = UserResponse.builder()
                .id(id)
                .roles(Set.of(USER_ROLE, "ROLE_MANAGER"))
                .build();

        when(userService.addRole(any(UUID.class), any(AddRoleRequest.class))).thenReturn(response);

        webTestClient.post().uri(BASE_URL + "/" + UUID.randomUUID() + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddRoleRequest("ROLE_MANAGER"))
                .header("X-User-Id", "admin-uuid")
                .header("X-User-Roles", ADMIN_ROLE)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.roles").isArray();
    }

    @Test
    void addRole_asUser_shouldReturn403() {
        UUID id = UUID.randomUUID();

        webTestClient.post().uri(BASE_URL + "/" + id + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddRoleRequest("ROLE_MANAGER"))
                .header("X-User-Id", id.toString())
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void addRole_withBlankRole_shouldReturn400() {
        UUID id = UUID.randomUUID();

        webTestClient.post().uri(BASE_URL + "/" + id + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AddRoleRequest(""))
                .header("X-User-Id", "admin-uuid")
                .header("X-User-Roles", ADMIN_ROLE)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.role").exists();
    }

    // ── removeRole ────────────────────────────────────────────────────────────

    @Test
    void removeRole_asAdmin_shouldReturn200() {
        UUID id = UUID.randomUUID();
        when(userService.removeRole(id, "ROLE_MANAGER")).thenReturn(
                UserResponse.builder().id(id).roles(Set.of(USER_ROLE)).build());

        webTestClient.delete().uri(BASE_URL + "/" + id + "/roles/ROLE_MANAGER")
                .header("X-User-Id", "admin-uuid")
                .header("X-User-Roles", ADMIN_ROLE)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void removeRole_asUser_shouldReturn403() {
        UUID id = UUID.randomUUID();

        webTestClient.delete().uri(BASE_URL + "/" + id + "/roles/ROLE_MANAGER")
                .header("X-User-Id", id.toString())
                .header("X-User-Roles", USER_ROLE)
                .exchange()
                .expectStatus().isForbidden();
    }
}