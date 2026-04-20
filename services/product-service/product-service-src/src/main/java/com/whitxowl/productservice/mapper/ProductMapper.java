package com.whitxowl.productservice.mapper;

import com.whitxowl.productservice.api.constant.ApiConstant;
import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.api.dto.request.UpdateProductRequest;
import com.whitxowl.productservice.api.dto.response.ProductImageResponse;
import com.whitxowl.productservice.api.dto.response.ProductResponse;
import com.whitxowl.productservice.domain.document.ProductDocument;
import com.whitxowl.productservice.domain.document.ProductImage;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductDocument toDocument(CreateProductRequest request);

    ProductResponse toResponse(ProductDocument document);

    @Mapping(target = "url", source = "imageId", qualifiedByName = "imageIdToUrl")
    ProductImageResponse toImageResponse(ProductImage image);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "visible", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDocument(UpdateProductRequest request, @MappingTarget ProductDocument document);

    @Named("imageIdToUrl")
    default String imageIdToUrl(String imageId) {
        return ApiConstant.PRODUCT_IMAGES_URL + imageId;
    }
}