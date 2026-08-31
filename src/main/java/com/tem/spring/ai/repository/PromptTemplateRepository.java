package com.tem.spring.ai.repository;

import com.tem.spring.ai.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, Long> {

    Optional<PromptTemplateEntity> findByTemplateKeyAndActiveTrue(String templateKey);

    Optional<PromptTemplateEntity> findByTemplateKey(String templateKey);
}
