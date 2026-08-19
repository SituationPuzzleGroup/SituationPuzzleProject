package com.situationpuzzle.repository;

import com.situationpuzzle.domain.AiPromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiPromptTemplateRepository extends JpaRepository<AiPromptTemplate, Long> {
    Optional<AiPromptTemplate> findByTemplateKeyAndEnabledTrue(String templateKey);
}
