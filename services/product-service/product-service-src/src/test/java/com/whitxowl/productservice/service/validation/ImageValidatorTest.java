package com.whitxowl.productservice.service.validation;

import com.whitxowl.productservice.exception.InvalidImageException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private final ImageValidator validator = new ImageValidator();

    @Test
    void validate_validJpeg_shouldPass() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[100]
        );
        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    void validate_validPng_shouldPass() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[100]
        );
        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    void validate_validWebp_shouldPass() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.webp", "image/webp", new byte[100]
        );
        assertThatNoException().isThrownBy(() -> validator.validate(file));
    }

    @Test
    void validate_emptyFile_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[0]
        );
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void validate_fileTooLarge_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[6 * 1024 * 1024]
        );
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void validate_invalidContentType_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "file.pdf", "application/pdf", new byte[100]
        );
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void validate_gifNotAllowed_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "anim.gif", "image/gif", new byte[100]
        );
        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(InvalidImageException.class);
    }
}