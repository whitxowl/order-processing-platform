package com.whitxowl.userservice.api.controller;

import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

import static com.whitxowl.userservice.api.constant.ApiConstant.USERS_URL;
import static com.whitxowl.userservice.api.constant.RoleConstant.ROLE_ADMIN;

@Tag(name = "User Controller", description = "Управление профилями пользователей и ролями")
@RequestMapping(USERS_URL)
public interface UserController {

    @Operation(
            summary = "Get user",
            description = "Получить профиль пользователя с ролями"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Профиль пользователя"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    ResponseEntity<UserResponse> getUser(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id
    );

    @Operation(
            summary = "Add role",
            description = "Назначить роль пользователю. Только ROLE_ADMIN"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Роль назначена"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured(ROLE_ADMIN)
    @PostMapping("/{id}/roles")
    ResponseEntity<UserResponse> addRole(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody AddRoleRequest request
    );

    @Operation(
            summary = "Remove role",
            description = "Отозвать роль у пользователя. Только ROLE_ADMIN"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Роль отозвана"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured(ROLE_ADMIN)
    @DeleteMapping("/{id}/roles/{role}")
    ResponseEntity<UserResponse> removeRole(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Название роли, например ROLE_MANAGER", required = true)
            @PathVariable String role
    );
}