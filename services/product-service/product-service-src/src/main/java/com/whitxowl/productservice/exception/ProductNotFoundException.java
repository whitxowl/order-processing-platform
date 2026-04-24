package com.whitxowl.productservice.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String id) {
        super("Product with id " + id + " not found");
    }
}