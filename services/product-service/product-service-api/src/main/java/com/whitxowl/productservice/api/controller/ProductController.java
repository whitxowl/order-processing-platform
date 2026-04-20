package com.whitxowl.productservice.api.controller;

import com.whitxowl.productservice.api.dto.request.CreateProductRequest;
import com.whitxowl.productservice.api.dto.request.UpdateProductRequest;
import com.whitxowl.productservice.api.dto.response.PageResponse;
import com.whitxowl.productservice.api.dto.response.ProductImageResponse;
import com.whitxowl.productservice.api.dto.response.ProductResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

import static com.whitxowl.productservice.api.constant.ApiConstant.PRODUCT_URL;
import static com.whitxowl.productservice.api.constant.RoleConstant.ROLE_ADMIN;
import static com.whitxowl.productservice.api.constant.RoleConstant.ROLE_MANAGER;

@Tag(name = "Product Controller", description = "Управление каталогом товаров")
@RequestMapping(PRODUCT_URL)
public interface ProductController {

    @Operation(summary = "Create product", description = "Создание нового товара. Доступно ROLE_MANAGER, ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно создан"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @PostMapping
    ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request);

    @Operation(summary = "Get product", description = "Получение товара по ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар найден"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @GetMapping("/{id}")
    ResponseEntity<ProductResponse> getProduct(
            @Parameter(description = "ID товара", required = true)
            @PathVariable("id") String id);

    @Operation(summary = "Update product", description = "Обновление товара. Доступно ROLE_MANAGER, ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар успешно обновлён"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @PutMapping("/{id}")
    ResponseEntity<ProductResponse> updateProduct(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateProductRequest request);

    @Operation(summary = "Delete product", description = "Удаление товара. Доступно только ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Товар успешно удалён"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_ADMIN})
    @DeleteMapping("/{id}")
    ResponseEntity<Void> deleteProduct(@PathVariable("id") String id);

    @Operation(summary = "Publish product", description = "Публикация товара (visible=true). Доступно ROLE_MANAGER, ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар опубликован"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @PatchMapping("/{id}/publish")
    ResponseEntity<ProductResponse> publishProduct(@PathVariable("id") String id);

    @Operation(summary = "Hide product", description = "Скрытие товара (visible=false). Доступно ROLE_MANAGER, ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Товар скрыт"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @PatchMapping("/{id}/hide")
    ResponseEntity<ProductResponse> hideProduct(@PathVariable("id") String id);

    @Operation(summary = "Search products", description = "Поиск товаров с фильтрами и пагинацией")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список товаров"),
            @ApiResponse(responseCode = "400", description = "Неправильные параметры запроса"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @GetMapping
    ResponseEntity<PageResponse<ProductResponse>> searchProducts(
            @Parameter(description = "Фильтр по категории")
            @RequestParam(name = "category", required = false) String category,
            @Parameter(description = "Минимальная цена")
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @Parameter(description = "Максимальная цена")
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @Parameter(description = "Полнотекстовый поиск по названию и описанию")
            @RequestParam(name = "text", required = false) String text,
            @Parameter(description = "Номер страницы (с 0)")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы")
            @RequestParam(name = "size", defaultValue = "20") int size);

    @Operation(summary = "Upload image", description = "Загрузка изображения товара. Доступно ROLE_MANAGER, ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Изображение загружено, возвращает обновлённый товар"),
            @ApiResponse(responseCode = "400", description = "Файл отсутствует или имеет недопустимый формат"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар не найден"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
    ResponseEntity<ProductResponse> uploadImage(
            @PathVariable("id") String id,
            @Parameter(description = "Файл изображения (jpeg, png, webp). Максимум 5 МБ", required = true)
            @RequestParam("file") MultipartFile file);

    @Operation(summary = "Delete image", description = "Удаление изображения товара. Доступно ROLE_MANAGER, ROLE_ADMIN")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Изображение удалено, возвращает обновлённый товар"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Товар или изображение не найдено"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @SecurityRequirement(name = "BearerAuth")
    @Secured({ROLE_MANAGER, ROLE_ADMIN})
    @DeleteMapping("/{id}/images/{imageId}")
    ResponseEntity<ProductResponse> deleteImage(
            @PathVariable("id") String id,
            @PathVariable("imageId") String imageId);

    @Operation(summary = "Get image", description = "Получение байтов изображения по imageId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Байты изображения"),
            @ApiResponse(responseCode = "404", description = "Изображение не найдено"),
            @ApiResponse(responseCode = "500", description = "Внутренняя ошибка сервера")
    })
    @GetMapping("/images/{imageId}")
    ResponseEntity<byte[]> getImage(@PathVariable("imageId") String imageId);
}