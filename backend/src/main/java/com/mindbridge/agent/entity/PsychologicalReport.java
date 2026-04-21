package com.mindbridge.agent.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "psychological_reports")
public class PsychologicalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String queryText;

    @Column(nullable = false, length = 32)
    private String intent;

    @Column(nullable = false, length = 16)
    private String riskLevel;

    @Column(nullable = false)
    private Integer ragContexts;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQueryText() {
        return queryText;
    }

    public void setQueryText(String queryText) {
        this.queryText = queryText;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Integer getRagContexts() {
        return ragContexts;
    }

    public void setRagContexts(Integer ragContexts) {
        this.ragContexts = ragContexts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
