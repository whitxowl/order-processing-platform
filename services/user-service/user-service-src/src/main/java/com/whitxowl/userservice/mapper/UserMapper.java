package com.whitxowl.userservice.mapper;

import com.whitxowl.userservice.api.dto.response.UserResponse;
import com.whitxowl.userservice.domain.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(target = "roles", expression = "java(entity.getRoleNames())")
    UserResponse toResponse(UserEntity entity);
}