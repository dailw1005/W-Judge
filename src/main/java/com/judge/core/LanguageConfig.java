package com.judge.core;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LanguageConfig {
    private String name;
    private String imageName; // Docker image
    private String compileCmd; // Command to compile
    private String runCmd; // Command to run
    private String sourceExtension; // .c, .cpp, .java
    private String srcFileName; // Name of the source file to save as (e.g. Main.java)
    private Long maxCpuTime; // Default limit
    private Long maxRealTime;
    private Long maxMemory;
    private Long maxStack;
}
