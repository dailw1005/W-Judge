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
public class Submission {
    private String id;
    private String sourceCode;
    private String language;
    private List<TestCase> testCases;
    private Long timeLimit; // milliseconds
    private Long memoryLimit; // bytes
    private Boolean earlyTermination; // true = stop on first failure, false/null = run all test cases
}
