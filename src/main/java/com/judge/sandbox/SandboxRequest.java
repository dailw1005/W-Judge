package com.judge.sandbox;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class SandboxRequest {
    private String imageName;
    private String containerName;
    private String[] command;
    private String input;
    private long timeLimit; // milliseconds
    private long memoryLimit; // bytes
    private Map<String, String> volumeMounts; // host path -> container path
    private boolean networkDisabled;
}
