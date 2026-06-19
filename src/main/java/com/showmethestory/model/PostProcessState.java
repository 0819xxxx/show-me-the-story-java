package com.showmethestory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Full post-process state persisted to postprocess.json.
 */
public class PostProcessState {

    @JsonProperty("diagnosis_report")
    private String diagnosisReport;

    @JsonProperty("consistency_report")
    private String consistencyReport;

    private List<RoadmapItem> roadmap;

    @JsonProperty("bundle_mode")
    private String bundleMode;

    @JsonProperty("volume_count")
    private int volumeCount;

    @JsonProperty("total_book_runes")
    private int totalBookRunes;

    @JsonProperty("estimated_tokens")
    private int estimatedTokens;

    @JsonProperty("diagnosed_at")
    private String diagnosedAt;

    @JsonProperty("consistency_at")
    private String consistencyAt;

    @JsonProperty("roadmap_at")
    private String roadmapAt;

    @JsonProperty("execute_options")
    private PostProcessExecuteOptions executeOptions;

    @JsonProperty("last_execute_at")
    private String lastExecuteAt;

    public PostProcessState() {}

    public String getDiagnosisReport() { return diagnosisReport; }
    public void setDiagnosisReport(String diagnosisReport) { this.diagnosisReport = diagnosisReport; }

    public String getConsistencyReport() { return consistencyReport; }
    public void setConsistencyReport(String consistencyReport) { this.consistencyReport = consistencyReport; }

    public List<RoadmapItem> getRoadmap() { return roadmap; }
    public void setRoadmap(List<RoadmapItem> roadmap) { this.roadmap = roadmap; }

    public String getBundleMode() { return bundleMode; }
    public void setBundleMode(String bundleMode) { this.bundleMode = bundleMode; }

    public int getVolumeCount() { return volumeCount; }
    public void setVolumeCount(int volumeCount) { this.volumeCount = volumeCount; }

    public int getTotalBookRunes() { return totalBookRunes; }
    public void setTotalBookRunes(int totalBookRunes) { this.totalBookRunes = totalBookRunes; }

    public int getEstimatedTokens() { return estimatedTokens; }
    public void setEstimatedTokens(int estimatedTokens) { this.estimatedTokens = estimatedTokens; }

    public String getDiagnosedAt() { return diagnosedAt; }
    public void setDiagnosedAt(String diagnosedAt) { this.diagnosedAt = diagnosedAt; }

    public String getConsistencyAt() { return consistencyAt; }
    public void setConsistencyAt(String consistencyAt) { this.consistencyAt = consistencyAt; }

    public String getRoadmapAt() { return roadmapAt; }
    public void setRoadmapAt(String roadmapAt) { this.roadmapAt = roadmapAt; }

    public PostProcessExecuteOptions getExecuteOptions() { return executeOptions; }
    public void setExecuteOptions(PostProcessExecuteOptions executeOptions) { this.executeOptions = executeOptions; }

    public String getLastExecuteAt() { return lastExecuteAt; }
    public void setLastExecuteAt(String lastExecuteAt) { this.lastExecuteAt = lastExecuteAt; }
}
