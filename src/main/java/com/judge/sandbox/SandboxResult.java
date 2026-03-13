package com.judge.sandbox;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SandboxResult {
    private String stdout;
    private String stderr;
    private int exitCode;
    private long timeUsed;
    private long memoryUsed;
    private boolean timeLimitExceeded;
    private boolean memoryLimitExceeded;
}
