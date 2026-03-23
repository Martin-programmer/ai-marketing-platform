package com.amp.config;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, UUID> {

    Optional<AiPromptTemplate> findByModuleAndPromptNameAndIsActiveTrue(String module, String promptName);

    List<AiPromptTemplate> findByModuleAndIsActiveTrueOrderByPromptName(String module);

    List<AiPromptTemplate> findByIsActiveTrueOrderByModuleAscPromptNameAsc();

    List<AiPromptTemplate> findByModuleAndPromptNameOrderByVersionDesc(String module, String promptName);

    Optional<AiPromptTemplate> findByModuleAndPromptNameAndVersion(String module, String promptName, int version);
}
