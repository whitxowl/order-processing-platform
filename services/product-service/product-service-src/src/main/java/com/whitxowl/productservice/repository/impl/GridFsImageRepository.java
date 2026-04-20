package com.whitxowl.productservice.repository.impl;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.whitxowl.productservice.exception.ImageNotFoundException;
import com.whitxowl.productservice.exception.InvalidImageException;
import com.whitxowl.productservice.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class GridFsImageRepository implements ImageRepository {

    private final GridFsTemplate gridFsTemplate;
    private final GridFsOperations gridFsOperations;

    @Override
    public String store(MultipartFile file) {
        try {
            String contentType = file.getContentType();
            String filename = StringUtils.cleanPath(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "image"
            );

            ObjectId fileId = gridFsTemplate.store(
                    file.getInputStream(),
                    filename,
                    contentType
            );

            log.info("Stored image: id={}, filename={}, contentType={}, size={}",
                    fileId, filename, contentType, file.getSize());

            return fileId.toHexString();

        } catch (IOException e) {
            throw new InvalidImageException("Failed to read image file: " + e.getMessage());
        }
    }

    @Override
    public ImageData load(String imageId) {
        GridFSFile file = gridFsTemplate.findOne(
                new Query(Criteria.where("_id").is(new ObjectId(imageId)))
        );

        if (file == null) {
            throw new ImageNotFoundException(imageId);
        }

        try {
            byte[] bytes = gridFsOperations.getResource(file).getInputStream().readAllBytes();
            String contentType = file.getMetadata() != null
                    ? file.getMetadata().getString("_contentType")
                    : "image/jpeg";

            return new ImageData(bytes, contentType);

        } catch (IOException e) {
            throw new InvalidImageException("Failed to read image from storage: " + e.getMessage());
        }
    }

    @Override
    public void delete(String imageId) {
        gridFsTemplate.delete(
                new Query(Criteria.where("_id").is(new ObjectId(imageId)))
        );
        log.info("Deleted image: id={}", imageId);
    }

    @Override
    public Set<String> findAllImageIds() {
        Set<String> ids = new HashSet<>();
        gridFsTemplate.find(new Query()).forEach(file ->
                ids.add(file.getObjectId().toHexString())
        );
        return ids;
    }

    @Override
    public void deleteOrphans(Collection<String> referencedImageIds) {
        Set<String> allIds = findAllImageIds();
        allIds.removeAll(referencedImageIds);

        allIds.forEach(imageId -> {
            gridFsTemplate.delete(
                    new Query(Criteria.where("_id").is(new ObjectId(imageId)))
            );
            log.info("Deleted orphan image: id={}", imageId);
        });

        if (!allIds.isEmpty()) {
            log.info("Deleted {} orphan images", allIds.size());
        }
    }
}