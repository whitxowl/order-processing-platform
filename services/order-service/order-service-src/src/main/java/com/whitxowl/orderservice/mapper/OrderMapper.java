package com.whitxowl.orderservice.mapper;

import com.whitxowl.orderservice.api.dto.response.OrderResponse;
import com.whitxowl.orderservice.domain.entity.OrderEntity;
import org.mapstruct.Mapper;

@Mapper
public interface OrderMapper {

    OrderResponse toOrderResponse(OrderEntity entity);
}