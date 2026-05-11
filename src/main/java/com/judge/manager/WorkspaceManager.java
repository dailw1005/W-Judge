package com.judge.manager;

import com.judge.config.JudgeProperties;
import com.judge.exception.JudgeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceManager {

    private final JudgeProperties judgeProperties;

    /**
     * Creates a unique workspace for a submission.
     * Path: {root}/compile-{submissionId}-{uuid}
     */
    public Path createSubmissionWorkspace(String submissionId) {
        try {
            Path root = Paths.get(judgeProperties.getWorkspace().getRoot());
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                root.toFile().setWritable(true, false);
                root.toFile().setReadable(true, false);
                root.toFile().setExecutable(true, false);
            }
            String uuid = UUID.randomUUID().toString();
            Path workDir = root.resolve("compile-" + submissionId + "-" + uuid);
            Files.createDirectories(workDir);
            // Make workspace world-writable so the container's nobody user can write to it
            workDir.toFile().setWritable(true, false);
            workDir.toFile().setReadable(true, false);
            workDir.toFile().setExecutable(true, false);
            return workDir;
        } catch (IOException e) {
            throw new JudgeException("Failed to create workspace for submission " + submissionId, e);
        }
    }

    /**
     * Writes source code to a file in the workspace.
     */
    public Path writeSourceCode(Path workDir, String fileName, String content) {
        try {
            Path sourceFile = workDir.resolve(fileName);
            Files.write(sourceFile, content.getBytes(StandardCharsets.UTF_8));
            return sourceFile;
        } catch (IOException e) {
            throw new JudgeException("Failed to write source code to " + fileName, e);
        }
    }

    /**
     * Writes input data for a specific test case.
     */
    public String writeTestCaseInput(Path workDir, int testCaseId, String input) {
        try {
            String inputFileName = "input_" + testCaseId + ".txt";
            Path inputPath = workDir.resolve(inputFileName);
            
            if (input != null) {
                Files.write(inputPath, input.getBytes(StandardCharsets.UTF_8));
            } else {
                Files.write(inputPath, new byte[0]);
            }
            return inputFileName;
        } catch (IOException e) {
            throw new JudgeException("Failed to write input file for test case " + testCaseId, e);
        }
    }

    /**
     * Cleans up the workspace directory.
     */
    public void cleanup(Path workDir) {
        if (workDir != null) {
            try {
                FileSystemUtils.deleteRecursively(workDir);
            } catch (IOException e) {
                log.warn("Failed to cleanup dir {}", workDir, e);
                // We don't throw exception here to avoid masking the original error if any
            }
        }
    }
}
