package com.judge.service;

import com.judge.config.JudgeProperties;
import com.judge.core.LanguageConfig;
import com.judge.core.Submission;
import com.judge.exception.CompilationException;
import com.judge.manager.WorkspaceManager;
import com.judge.sandbox.Sandbox;
import com.judge.sandbox.SandboxRequest;
import com.judge.sandbox.SandboxResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompilerService {

    private final Sandbox sandbox;
    private final WorkspaceManager workspaceManager;
    private final JudgeProperties judgeProperties;

    public CompileResult compile(Submission submission, LanguageConfig config) {
        Path workDir = workspaceManager.createSubmissionWorkspace(submission.getId());

        try {
            // Write source code
            workspaceManager.writeSourceCode(workDir, config.getSrcFileName(), submission.getSourceCode());

            String workDirName = workDir.getFileName().toString();
            // Fix permissions after compile so the host (dailw user) can clean up later.
            // Files created by the container's nobody user (e.g. __pycache__) are otherwise
            // undeletable by the Java process on the host.
            String fullCmd = String.format(
                    "cd /app/%s && (%s); rc=$?; chmod -R 777 . 2>/dev/null; exit $rc",
                    workDirName, config.getCompileCmd());

            SandboxRequest request = SandboxRequest.builder()
                    .imageName(config.getImageName())
                    .containerName("compile-" + UUID.randomUUID())
                    .command(new String[]{"sh", "-c", fullCmd})
                    .timeLimit(judgeProperties.getCompiler().getTimeLimit())
                    .memoryLimit(judgeProperties.getCompiler().getMemoryLimit())
                    .networkDisabled(true) // No network during compile
                    .volumeMounts(Map.of(workDir.toAbsolutePath().toString(), "/app"))
                    .build();

            SandboxResult result = sandbox.run(request);

            log.debug("Compile cmd: {}, exitCode: {}, timeLimitExceeded: {}", fullCmd, result.getExitCode(), result.isTimeLimitExceeded());

            if (result.isTimeLimitExceeded()) {
                return CompileResult.builder()
                        .success(false)
                        .message("Compilation timed out\n" + result.getStderr() + "\n" + result.getStdout())
                        .workDir(workDir.toAbsolutePath().toString())
                        .build();
            }

            if (result.getExitCode() != 0) {
                return CompileResult.builder()
                        .success(false)
                        .message(result.getStderr() + "\n" + result.getStdout())
                        .workDir(workDir.toAbsolutePath().toString())
                        .build();
            }

            return CompileResult.builder()
                    .success(true)
                    .workDir(workDir.toAbsolutePath().toString())
                    .build();

        } catch (Exception e) {
            log.error("Compilation failed", e);
            workspaceManager.cleanup(workDir);
            throw new CompilationException("Compilation process failed", e);
        }
    }
}
