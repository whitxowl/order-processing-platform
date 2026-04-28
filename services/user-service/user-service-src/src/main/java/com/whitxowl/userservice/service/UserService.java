package com.whitxowl.userservice.service;

import com.whitxowl.userservice.api.dto.request.AddRoleRequest;
import com.whitxowl.userservice.api.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    void createIfAbsent(String userId, String email);

    UserResponse getUser(UUID id);

    UserResponse addRole(UUID id, AddRoleRequest request);

    UserResponse removeRole(UUID id, String role);
}