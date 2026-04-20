package com.whitxowl.productservice.repository;

import com.whitxowl.productservice.domain.document.ProductDocument;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Set;

public interface ProductRepository
        extends MongoRepository<ProductDocument, String>, ProductRepositoryCustom {

    @Aggregation(pipeline = {
            "{ $unwind: '$images' }",
            "{ $group: { _id: null, imageIds: { $addToSet: '$images.imageId' } } }",
            "{ $project: { _id: 0, imageIds: 1 } }"
    })
    ImageIdsResult findAllReferencedImageIdsRaw();

    default Set<String> findAllReferencedImageIds() {
        ImageIdsResult result = findAllReferencedImageIdsRaw();
        return result != null ? result.imageIds() : Set.of();
    }

    record ImageIdsResult(Set<String> imageIds) {}
}