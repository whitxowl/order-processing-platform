package com.whitxowl.inventoryservice.mapper;

import com.whitxowl.inventoryservice.api.dto.response.ReservationResponse;
import com.whitxowl.inventoryservice.api.dto.response.StockResponse;
import com.whitxowl.inventoryservice.domain.entity.InventoryItemEntity;
import com.whitxowl.inventoryservice.domain.entity.ReservationEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface InventoryMapper {

    @Mapping(target = "available", expression = "java(entity.getAvailable())")
    StockResponse toStockResponse(InventoryItemEntity entity);

    ReservationResponse toReservationResponse(ReservationEntity entity);
}