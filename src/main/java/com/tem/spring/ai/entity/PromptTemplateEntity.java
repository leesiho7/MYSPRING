package com.tem.spring.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 프롬프트 템플릿 외부화 및 DB 관리 엔티티 (Prompt De-hardcoding)
 */
@Entity
@Table(name = "prompt_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromptTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", unique = true, nullable = false, length = 100)
    private String templateKey;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Lob
    @Column(name = "template_content", nullable = false, columnDefinition = "LONGTEXT")
    private String templateContent;

    @Column(name = "category", length = 50)
    private String category; // SYSTEM, USER, PERSONA, SENTIMENT

    @Column(name = "version", length = 20)
    private String version;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
