package com.whitxowl.userservice.controller;

import com.whitxowl.userservice.api.controller.UserController;
import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;
import com.whitxowl.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {

    private final UserService userService;

    @Override
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUser(id));
    }

    @Override
    public ResponseEntity<UserResponse> addRole(
            @PathVariable UUID id,
            @Valid @RequestBody AddRoleRequest request
    ) {
        return ResponseEntity.ok(userService.addRole(id, request));
    }

    @Override
    public ResponseEntity<UserResponse> removeRole(
            @PathVariable UUID id,
            @PathVariable String role
    ) {
        return ResponseEntity.ok(userService.removeRole(id, role));
    }
}