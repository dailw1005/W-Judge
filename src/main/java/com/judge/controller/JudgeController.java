package com.judge.controller;

import com.judge.core.JudgeResult;
import com.judge.core.Submission;
import com.judge.service.JudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/judge")
@RequiredArgsConstructor
public class JudgeController {

    private final JudgeService judgeService;

    @PostMapping
    public ResponseEntity<JudgeResult> judge(@RequestBody Submission submission) {
        return ResponseEntity.ok(judgeService.judge(submission));
    }
}
