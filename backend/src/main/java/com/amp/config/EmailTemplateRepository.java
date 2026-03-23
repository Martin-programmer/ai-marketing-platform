package com.amp.config;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplateEntity, UUID> {

    Optional<EmailTemplateEntity> findByTemplateKeyAndIsActiveTrue(String templateKey);

    List<EmailTemplateEntity> findByIsActiveTrueOrderByTemplateKey();
}
