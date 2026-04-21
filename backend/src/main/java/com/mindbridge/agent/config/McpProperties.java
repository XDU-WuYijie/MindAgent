package com.mindbridge.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mindbridge.mcp")
public class McpProperties {

    private boolean enabled = true;
    private Endpoint excel = new Endpoint();
    private Endpoint email = new Endpoint();
    private Retry retry = new Retry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Endpoint getExcel() {
        return excel;
    }

    public void setExcel(Endpoint excel) {
        this.excel = excel;
    }

    public Endpoint getEmail() {
        return email;
    }

    public void setEmail(Endpoint email) {
        this.email = email;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public static class Endpoint {
        private String url = "";
        private boolean enabled = true;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private long delayMs = 300;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
