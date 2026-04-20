package com.whitxowl.productservice.scheduler;

import com.whitxowl.productservice.repository.ImageRepository;
import com.whitxowl.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanImageCleanupJob {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;

    @Scheduled(cron = "${app.cleanup.orphan-images-cron:0 0 3 * * SUN}")
    public void cleanOrphanImages() {
        log.info("Starting orphan image cleanup");

        Set<String> referencedImageIds = productRepository.findAllReferencedImageIds();
        log.info("Found {} referenced images", referencedImageIds.size());

        imageRepository.deleteOrphans(referencedImageIds);

        log.info("Orphan image cleanup finished");
    }
}