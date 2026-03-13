package com.judge.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseResult {
    private int testCaseId;
    private JudgeStatus status;
    private Long timeUsed;
    private Long memoryUsed;
    private String output;
    private String message;
}
