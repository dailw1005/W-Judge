package com.judge.core;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResult {
    private String submissionId;
    private JudgeStatus status;
    private Long timeUsed; // milliseconds (max)
    private Long memoryUsed; // bytes (max)
    private String message; // Compiler error or general error message
    private List<TestCaseResult> testCaseResults;
}
