package com.dlqrevive.redrive;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RedriveMessage {
    private String topic;
    private int partition;
    private long offset;
    private String key;
    private String value;
}
