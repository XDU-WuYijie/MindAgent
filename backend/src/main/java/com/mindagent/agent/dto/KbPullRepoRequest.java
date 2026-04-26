package com.mindagent.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class KbPullRepoRequest {

    @NotBlank
    private String repoUrl;
    private String branch = "main";
    private String subPath = "";

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getSubPath() {
        return subPath;
    }

    public void setSubPath(String subPath) {
        this.subPath = subPath;
    }
}
