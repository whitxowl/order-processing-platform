package com.whitxowl.productservice.scheduler;

import com.whitxowl.productservice.repository.ImageRepository;
import com.whitxowl.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanImageCleanupJobTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ImageRepository imageRepository;

    @InjectMocks
    private OrphanImageCleanupJob cleanupJob;

    @Test
    void cleanOrphanImages_shouldPassReferencedIdsToRepository() {
        Set<String> referencedIds = Set.of("img-1", "img-2");
        when(productRepository.findAllReferencedImageIds()).thenReturn(referencedIds);

        cleanupJob.cleanOrphanImages();

        verify(productRepository).findAllReferencedImageIds();
        verify(imageRepository).deleteOrphans(referencedIds);
    }

    @Test
    void cleanOrphanImages_noReferencedImages_shouldPassEmptySet() {
        when(productRepository.findAllReferencedImageIds()).thenReturn(Set.of());

        cleanupJob.cleanOrphanImages();

        verify(imageRepository).deleteOrphans(Set.of());
    }
}