package com.whitxowl.authservice.mapper;

import com.whitxowl.authservice.api.dto.response.UserResponse;
import com.whitxowl.authservice.domain.entity.RoleEntity;
import com.whitxowl.authservice.domain.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "rolesToStrings")
    UserResponse toUserResponse(UserEntity user);

    @Named("rolesToStrings")
    default Set<String> rolesToStrings(Set<RoleEntity> roles) {
        if (roles == null) {
            return Set.of();
        }
        return roles.stream()
                .map(RoleEntity::getRole)
                .collect(Collectors.toSet());
    }
}
