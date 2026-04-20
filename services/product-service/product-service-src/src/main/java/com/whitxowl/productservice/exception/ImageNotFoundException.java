package com.whitxowl.productservice.exception;

public class ImageNotFoundException extends RuntimeException {

    public ImageNotFoundException(String imageId) {
        super("Image with id " + imageId + " not found");
    }
}