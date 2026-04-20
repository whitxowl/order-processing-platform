package com.whitxowl.productservice.repository;

import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Set;

public interface ImageRepository {

    String store(MultipartFile file);

    ImageData load(String imageId);

    void delete(String imageId);

    Set<String> findAllImageIds();

    void deleteOrphans(Collection<String> referencedImageIds);

    record ImageData(byte[] bytes, String contentType) {}
}