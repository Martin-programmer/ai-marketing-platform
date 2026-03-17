package com.amp.creatives;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreativePackageItemRepository extends JpaRepository<CreativePackageItem, UUID> {

    List<CreativePackageItem> findAllByPackageId(UUID packageId);

    List<CreativePackageItem> findAllByPackageIdOrderByCreatedAtAsc(UUID packageId);

    java.util.Optional<CreativePackageItem> findByIdAndPackageId(UUID id, UUID packageId);
}
