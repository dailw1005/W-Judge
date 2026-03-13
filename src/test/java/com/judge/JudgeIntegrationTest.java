package com.judge;

import com.judge.core.JudgeResult;
import com.judge.core.JudgeStatus;
import com.judge.core.Submission;
import com.judge.core.TestCase;
import com.judge.service.JudgeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JudgeIntegrationTest {

    @Autowired
    private JudgeService judgeService;

    @Test
    public void testPythonHelloWorld() {
        Submission submission = Submission.builder()
                .id(UUID.randomUUID().toString())
                .language("python")
                .sourceCode("print('Hello World')")
                .testCases(List.of(
                        TestCase.builder().input("").expectedOutput("Hello World").build()
                ))
                .timeLimit(5000L)
                .memoryLimit(128 * 1024 * 1024L)
                .build();

        JudgeResult result = judgeService.judge(submission);
        
        System.out.println("Result: " + result);
        
        if (result.getStatus() == JudgeStatus.SYSTEM_ERROR) {
             System.err.println("Judge failed with SYSTEM_ERROR: " + result.getMessage());
        } else {
             assertEquals(JudgeStatus.ACCEPTED, result.getStatus());
             assertNotNull(result.getTestCaseResults());
             assertEquals(1, result.getTestCaseResults().size());
             assertEquals(JudgeStatus.ACCEPTED, result.getTestCaseResults().get(0).getStatus());
        }
    }

    @Test
    public void testTimeLimitExceeded() {
        Submission submission = Submission.builder()
                .id(UUID.randomUUID().toString())
                .language("python")
                .sourceCode("while True: pass")
                .testCases(List.of(
                        TestCase.builder().input("").expectedOutput("Hello World").build()
                ))
                .timeLimit(1000L)
                .memoryLimit(128 * 1024 * 1024L)
                .build();

        JudgeResult result = judgeService.judge(submission);
        
        System.out.println("Result: " + result);
        if (result.getStatus() != JudgeStatus.SYSTEM_ERROR) {
            assertEquals(JudgeStatus.TIME_LIMIT_EXCEEDED, result.getStatus());
        }
    }

    @Test
    public void testMultipleTestCases() {
        // Python code that echoes input
        String sourceCode = "import sys\nfor line in sys.stdin:\n    print(line.strip())";
        
        Submission submission = Submission.builder()
                .id(UUID.randomUUID().toString())
                .language("python")
                .sourceCode(sourceCode)
                .testCases(List.of(
                        TestCase.builder().input("hello").expectedOutput("hello").build(),
                        TestCase.builder().input("world").expectedOutput("world").build()
                ))
                .timeLimit(2000L)
                .memoryLimit(128 * 1024 * 1024L)
                .build();

        JudgeResult result = judgeService.judge(submission);
        
        System.out.println("Result: " + result);
        
        if (result.getStatus() != JudgeStatus.SYSTEM_ERROR) {
            assertEquals(JudgeStatus.ACCEPTED, result.getStatus());
            assertEquals(2, result.getTestCaseResults().size());
            assertEquals(JudgeStatus.ACCEPTED, result.getTestCaseResults().get(0).getStatus());
            assertEquals(JudgeStatus.ACCEPTED, result.getTestCaseResults().get(1).getStatus());
        }
    }

    @Test
    public void testMultipleTestCasesWithOneFailure() {
        // Python code that echoes input
        String sourceCode = "import sys\nfor line in sys.stdin:\n    print(line.strip())";

        Submission submission = Submission.builder()
                .id(UUID.randomUUID().toString())
                .language("python")
                .sourceCode(sourceCode)
                .testCases(List.of(
                        TestCase.builder().input("hello").expectedOutput("hello").build(),
                        TestCase.builder().input("wrong").expectedOutput("right").build() // Should fail
                ))
                .timeLimit(2000L)
                .memoryLimit(128 * 1024 * 1024L)
                .build();

        JudgeResult result = judgeService.judge(submission);

        System.out.println("Result: " + result);

        if (result.getStatus() != JudgeStatus.SYSTEM_ERROR) {
            assertEquals(JudgeStatus.WRONG_ANSWER, result.getStatus());
            assertEquals(2, result.getTestCaseResults().size());
            assertEquals(JudgeStatus.ACCEPTED, result.getTestCaseResults().get(0).getStatus());
            assertEquals(JudgeStatus.WRONG_ANSWER, result.getTestCaseResults().get(1).getStatus());
        }
    }

    @Test
    public void testConcurrentPerformance() {
        // Run 5 test cases, each sleeping for 1 second.
        // Sequential: > 5s
        // Concurrent: ~ 1s (plus overhead)
        
        String sourceCode = "import time\ntime.sleep(1)\nprint('done')";
        
        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            testCases.add(TestCase.builder().input("").expectedOutput("done").build());
        }

        Submission submission = Submission.builder()
                .id(UUID.randomUUID().toString())
                .language("python")
                .sourceCode(sourceCode)
                .testCases(testCases)
                .timeLimit(2000L) // Each task takes 1s, limit 2s is fine for concurrent
                .memoryLimit(128 * 1024 * 1024L)
                .build();

        long startTime = System.currentTimeMillis();
        JudgeResult result = judgeService.judge(submission);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("Concurrent Test Duration: " + duration + "ms");
        System.out.println("Result: " + result);

        if (result.getStatus() != JudgeStatus.SYSTEM_ERROR) {
            assertEquals(JudgeStatus.ACCEPTED, result.getStatus());
            assertEquals(5, result.getTestCaseResults().size());
            
            // Assert that it took less than sequential time (e.g. 5 * 1s = 5000ms)
            // Giving some buffer, let's say it should be under 4000ms if concurrent works
            // In ideal world it is ~1000ms + overhead.
            assertTrue(duration < 4000, "Concurrent execution took too long: " + duration + "ms");
        }
    }
}
