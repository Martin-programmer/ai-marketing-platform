package com.amp.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private Anthropic anthropic = new Anthropic();
    private Optimizer optimizer = new Optimizer();
    private Analyzer analyzer = new Analyzer();
    private CostLimits costLimits = new CostLimits();

    public Anthropic getAnthropic() { return anthropic; }
    public void setAnthropic(Anthropic v) { this.anthropic = v; }
    public Optimizer getOptimizer() { return optimizer; }
    public void setOptimizer(Optimizer v) { this.optimizer = v; }
    public Analyzer getAnalyzer() { return analyzer; }
    public void setAnalyzer(Analyzer v) { this.analyzer = v; }
    public CostLimits getCostLimits() { return costLimits; }
    public void setCostLimits(CostLimits v) { this.costLimits = v; }

    public static class Anthropic {
        private String apiKey;
        private String defaultModel = "claude-sonnet-4-20250514";
        private String complexModel = "claude-opus-4-20250115";
        private String apiBaseUrl = "https://api.anthropic.com";
        private String apiVersion = "2023-06-01";
        private int maxTokens = 4096;
        private int timeoutSeconds = 60;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getDefaultModel() { return defaultModel; }
        public void setDefaultModel(String v) { this.defaultModel = v; }
        public String getComplexModel() { return complexModel; }
        public void setComplexModel(String v) { this.complexModel = v; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String v) { this.apiBaseUrl = v; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String v) { this.apiVersion = v; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int v) { this.maxTokens = v; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int v) { this.timeoutSeconds = v; }
    }

    public static class Optimizer {
        private boolean enabled = true;
        private String scheduleCron = "0 0 5 * * *";
        private int minDataDays = 7;
        private int minConversions = 30;
        private double frequencyThreshold = 2.0;
        private double ctrDropThreshold = 0.15;
        private double cpaSpikeThreshold = 0.40;
        private int budgetChangeMaxPercent = 10;
        private int budgetCumulativeMaxPercent = 25;
        private int cooldownBudgetHours = 72;
        private int cooldownPauseHours = 48;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getScheduleCron() { return scheduleCron; }
        public void setScheduleCron(String v) { this.scheduleCron = v; }
        public int getMinDataDays() { return minDataDays; }
        public void setMinDataDays(int v) { this.minDataDays = v; }
        public int getMinConversions() { return minConversions; }
        public void setMinConversions(int v) { this.minConversions = v; }
        public double getFrequencyThreshold() { return frequencyThreshold; }
        public void setFrequencyThreshold(double v) { this.frequencyThreshold = v; }
        public double getCtrDropThreshold() { return ctrDropThreshold; }
        public void setCtrDropThreshold(double v) { this.ctrDropThreshold = v; }
        public double getCpaSpikeThreshold() { return cpaSpikeThreshold; }
        public void setCpaSpikeThreshold(double v) { this.cpaSpikeThreshold = v; }
        public int getBudgetChangeMaxPercent() { return budgetChangeMaxPercent; }
        public void setBudgetChangeMaxPercent(int v) { this.budgetChangeMaxPercent = v; }
        public int getBudgetCumulativeMaxPercent() { return budgetCumulativeMaxPercent; }
        public void setBudgetCumulativeMaxPercent(int v) { this.budgetCumulativeMaxPercent = v; }
        public int getCooldownBudgetHours() { return cooldownBudgetHours; }
        public void setCooldownBudgetHours(int v) { this.cooldownBudgetHours = v; }
        public int getCooldownPauseHours() { return cooldownPauseHours; }
        public void setCooldownPauseHours(int v) { this.cooldownPauseHours = v; }
    }

    public static class Analyzer {
        private boolean enabled = true;
        private boolean autoAnalyzeOnUpload = true;
        private boolean autoGenerateCopy = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public boolean isAutoAnalyzeOnUpload() { return autoAnalyzeOnUpload; }
        public void setAutoAnalyzeOnUpload(boolean v) { this.autoAnalyzeOnUpload = v; }
        public boolean isAutoGenerateCopy() { return autoGenerateCopy; }
        public void setAutoGenerateCopy(boolean v) { this.autoGenerateCopy = v; }
    }

    public static class CostLimits {
        private double maxDailySpendUsd = 5.0;
        private double maxMonthlySpendUsd = 100.0;
        private int alertThresholdPercent = 80;

        public double getMaxDailySpendUsd() { return maxDailySpendUsd; }
        public void setMaxDailySpendUsd(double v) { this.maxDailySpendUsd = v; }
        public double getMaxMonthlySpendUsd() { return maxMonthlySpendUsd; }
        public void setMaxMonthlySpendUsd(double v) { this.maxMonthlySpendUsd = v; }
        public int getAlertThresholdPercent() { return alertThresholdPercent; }
        public void setAlertThresholdPercent(int v) { this.alertThresholdPercent = v; }
    }
}
