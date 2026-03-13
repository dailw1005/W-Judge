package com.judge.service;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompileResult {
    private boolean success;
    private String message;
    private String workDir; // Path to directory containing executable
    private String executableName;
}
