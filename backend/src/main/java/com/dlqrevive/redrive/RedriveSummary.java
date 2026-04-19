package com.dlqrevive.redrive;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedriveSummary {
    private int produced;
    private int skipped;
    private int failed;
    private String targetTopic;
    private String sessionId;
}
