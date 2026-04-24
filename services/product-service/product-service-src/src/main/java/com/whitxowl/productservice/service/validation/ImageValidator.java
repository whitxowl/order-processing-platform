package com.whitxowl.productservice.service.validation;

import com.whitxowl.productservice.exception.InvalidImageException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class ImageValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    public void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidImageException("File size exceeds 5 MB limit");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidImageException(
                    "Invalid file type. Allowed: jpeg, png, webp"
            );
        }
    }
}