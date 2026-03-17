package com.judge.service;

import com.judge.config.JudgeProperties;
import com.judge.config.LanguageConfigLoader;
import com.judge.core.*;
import com.judge.exception.CompilationException;
import com.judge.manager.WorkspaceManager;
import com.judge.sandbox.Sandbox;
import com.judge.sandbox.SandboxRequest;
import com.judge.sandbox.SandboxResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JudgeService {

    private final LanguageConfigLoader configLoader;
    private final CompilerService compilerService;
    private final Sandbox sandbox;
    private final MeterRegistry meterRegistry;
    private final WorkspaceManager workspaceManager;
    private final JudgeProperties judgeProperties;

    @Qualifier("judgeExecutor")
    private final Executor judgeExecutor;

    public JudgeResult judge(Submission submission) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            JudgeResult result = doJudge(submission);
            meterRegistry.counter("judge.submissions.total", "language", submission.getLanguage(), "status", result.getStatus().name()).increment();
            return result;
        } finally {
            sample.stop(meterRegistry.timer("judge.duration", "language", submission.getLanguage()));
        }
    }

    private JudgeResult doJudge(Submission submission) {
        LanguageConfig config = configLoader.getConfig(submission.getLanguage());
        if (config == null) {
            return JudgeResult.builder()
                    .submissionId(submission.getId())
                    .status(JudgeStatus.SYSTEM_ERROR)
                    .message("Language not supported: " + submission.getLanguage())
                    .build();
        }

        Path workDir = null;
        try {
            // 1. Compile
            CompileResult compileResult = compilerService.compile(submission, config);
            workDir = Paths.get(compileResult.getWorkDir());

            if (!compileResult.isSuccess()) {
                return JudgeResult.builder()
                        .submissionId(submission.getId())
                        .status(JudgeStatus.COMPILATION_ERROR)
                        .message(compileResult.getMessage())
                        .build();
            }

            List<TestCase> testCases = submission.getTestCases();
            if (testCases == null || testCases.isEmpty()) {
                return JudgeResult.builder()
                        .submissionId(submission.getId())
                        .status(JudgeStatus.SYSTEM_ERROR)
                        .message("No test cases provided")
                        .build();
            }

            // 2. Run Test Cases Concurrently
            List<CompletableFuture<TestCaseResult>> futures = new ArrayList<>();
            // Need final path for lambda
            Path finalWorkDir = workDir;
            
            for (int i = 0; i < testCases.size(); i++) {
                int testCaseId = i + 1;
                TestCase testCase = testCases.get(i);
                
                CompletableFuture<TestCaseResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return runTestCase(testCaseId, testCase, submission, config, finalWorkDir);
                    } catch (Exception e) {
                        log.error("Error running test case {}", testCaseId, e);
                        return TestCaseResult.builder()
                                .testCaseId(testCaseId)
                                .status(JudgeStatus.SYSTEM_ERROR)
                                .message("System error: " + e.getMessage())
                                .build();
                    }
                }, judgeExecutor);
                futures.add(future);
            }

            // Wait for all
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<TestCaseResult> testCaseResults = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // 3. Aggregate Results
            long maxTime = 0;
            long maxMemory = 0;
            JudgeStatus finalStatus = JudgeStatus.ACCEPTED;

            // Sort results by ID just in case
            testCaseResults.sort(Comparator.comparingInt(TestCaseResult::getTestCaseId));

            for (TestCaseResult result : testCaseResults) {
                maxTime = Math.max(maxTime, result.getTimeUsed() != null ? result.getTimeUsed() : 0);
                maxMemory = Math.max(maxMemory, result.getMemoryUsed() != null ? result.getMemoryUsed() : 0);

                if (finalStatus == JudgeStatus.ACCEPTED && result.getStatus() != JudgeStatus.ACCEPTED) {
                    finalStatus = result.getStatus();
                }
            }

            return JudgeResult.builder()
                    .submissionId(submission.getId())
                    .status(finalStatus)
                    .timeUsed(maxTime)
                    .memoryUsed(maxMemory)
                    .testCaseResults(testCaseResults)
                    .build();

        } catch (CompilationException e) {
            log.error("Compilation system error", e);
            return JudgeResult.builder()
                    .submissionId(submission.getId())
                    .status(JudgeStatus.SYSTEM_ERROR)
                    .message("System error during compilation: " + e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Judge error", e);
            return JudgeResult.builder()
                    .submissionId(submission.getId())
                    .status(JudgeStatus.SYSTEM_ERROR)
                    .message(e.getMessage())
                    .build();
        } finally {
            workspaceManager.cleanup(workDir);
        }
    }

    private TestCaseResult runTestCase(int id, TestCase testCase, Submission submission, LanguageConfig config, Path workDir) throws IOException {
        // Unique input file for this test case to avoid race conditions
        String inputFileName = workspaceManager.writeTestCaseInput(workDir, id, testCase.getInput());

        // Run
        String runCmd = config.getRunCmd();
        String workDirName = workDir.getFileName().toString();
        // Updated command to read from specific input file and ensure permissions for cleanup
        String fullCmd = String.format("cd /app/%s && %s < %s; CODE=$?; chmod -R 777 .; exit $CODE", workDirName, runCmd, inputFileName);

        SandboxRequest request = SandboxRequest.builder()
                .imageName(config.getImageName())
                .containerName("run-" + UUID.randomUUID())
                .command(new String[]{"sh", "-c", fullCmd})
                .timeLimit(submission.getTimeLimit() != null ? submission.getTimeLimit() : config.getMaxCpuTime())
                .memoryLimit(submission.getMemoryLimit() != null ? submission.getMemoryLimit() : config.getMaxMemory())
                .networkDisabled(true)
                .volumeMounts(Map.of(workDir.toAbsolutePath().toString(), "/app"))
                .build();

        SandboxResult sandboxResult = sandbox.run(request);

        // Build Result
        TestCaseResult.TestCaseResultBuilder resultBuilder = TestCaseResult.builder()
                .testCaseId(id)
                .timeUsed(sandboxResult.getTimeUsed())
                .memoryUsed(sandboxResult.getMemoryUsed())
                .output(sandboxResult.getStdout())
                .message(sandboxResult.getStderr());

        if (sandboxResult.isTimeLimitExceeded()) {
            resultBuilder.status(JudgeStatus.TIME_LIMIT_EXCEEDED);
        } else if (sandboxResult.isMemoryLimitExceeded()) {
            resultBuilder.status(JudgeStatus.MEMORY_LIMIT_EXCEEDED);
        } else if (sandboxResult.getExitCode() != 0) {
            resultBuilder.status(JudgeStatus.RUNTIME_ERROR);
            resultBuilder.message("Exit code: " + sandboxResult.getExitCode() + "\n" + sandboxResult.getStderr());
        } else {
            String expected = testCase.getExpectedOutput();
            String actual = sandboxResult.getStdout();

            if (expected == null) expected = "";
            if (actual == null) actual = "";

            if (expected.trim().equals(actual.trim())) {
                resultBuilder.status(JudgeStatus.ACCEPTED);
            } else {
                resultBuilder.status(JudgeStatus.WRONG_ANSWER);
            }
        }

        return resultBuilder.build();
    }
}
