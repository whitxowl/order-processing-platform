package com.whitxowl.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;
import com.whitxowl.userservice.exception.UserNotFoundException;
import com.whitxowl.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UserControllerImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private static final String BASE_URL   = "/api/v1/users";
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String USER_ROLE  = "ROLE_USER";

    // ── getUser ───────────────────────────────────────────────────────────────

    @Test
    void getUser_shouldReturn200_whenAuthenticated() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenReturn(UserResponse.builder()
                .id(id).email("user@example.com").roles(Set.of(USER_ROLE)).build());

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("X-User-Id", id.toString())
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void getUser_shouldReturn401_whenNoHeaders() throws Exception {
        mockMvc.perform(get(BASE_URL + "/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUser_shouldReturn404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUser(id)).thenThrow(new UserNotFoundException(id));

        mockMvc.perform(get(BASE_URL + "/" + id)
                        .header("X-User-Id", id.toString())
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isNotFound());
    }

    // ── addRole ───────────────────────────────────────────────────────────────

    @Test
    void addRole_asAdmin_shouldReturn200() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(UUID.randomUUID())
                .roles(Set.of(USER_ROLE, "ROLE_MANAGER"))
                .build();

        when(userService.addRole(any(UUID.class), any(AddRoleRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post(BASE_URL + "/" + UUID.randomUUID() + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddRoleRequest("ROLE_MANAGER")))
                        .header("X-User-Id", "admin-uuid")
                        .header("X-User-Roles", ADMIN_ROLE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void addRole_asUser_shouldReturn403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/" + id + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddRoleRequest("ROLE_MANAGER")))
                        .header("X-User-Id", id.toString())
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isForbidden());
    }

    @Test
    void addRole_withBlankRole_shouldReturn400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/" + id + "/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddRoleRequest("")))
                        .header("X-User-Id", "admin-uuid")
                        .header("X-User-Roles", ADMIN_ROLE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    // ── removeRole ────────────────────────────────────────────────────────────

    @Test
    void removeRole_asAdmin_shouldReturn200() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.removeRole(id, "ROLE_MANAGER"))
                .thenReturn(UserResponse.builder()
                        .id(id).roles(Set.of(USER_ROLE)).build());

        mockMvc.perform(delete(BASE_URL + "/" + id + "/roles/ROLE_MANAGER")
                        .header("X-User-Id", "admin-uuid")
                        .header("X-User-Roles", ADMIN_ROLE))
                .andExpect(status().isOk());
    }

    @Test
    void removeRole_asUser_shouldReturn403() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete(BASE_URL + "/" + id + "/roles/ROLE_MANAGER")
                        .header("X-User-Id", id.toString())
                        .header("X-User-Roles", USER_ROLE))
                .andExpect(status().isForbidden());
    }
}