package com.judge.config;

import com.judge.core.LanguageConfig;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LanguageConfigLoader {

    private final Map<String, LanguageConfig> languageConfigMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadConfigs() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("languages.yaml")) {
            Yaml yaml = new Yaml();
            Map<String, List<Map<String, Object>>> obj = yaml.load(inputStream);
            List<Map<String, Object>> languages = obj.get("languages");

            for (Map<String, Object> langMap : languages) {
                LanguageConfig config = new LanguageConfig();
                config.setName((String) langMap.get("name"));
                config.setImageName((String) langMap.get("imageName"));
                config.setSourceExtension((String) langMap.get("sourceExtension"));
                config.setSrcFileName((String) langMap.get("srcFileName"));
                config.setCompileCmd((String) langMap.get("compileCmd"));
                config.setRunCmd((String) langMap.get("runCmd"));
                
                config.setMaxCpuTime(convertToLong(langMap.get("maxCpuTime")));
                config.setMaxMemory(convertToLong(langMap.get("maxMemory")));
                
                languageConfigMap.put(config.getName(), config);
                log.info("Loaded language config: {}", config.getName());
            }
        } catch (Exception e) {
            log.error("Failed to load language configs", e);
            throw new RuntimeException(e);
        }
    }

    private Long convertToLong(Object obj) {
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        } else if (obj instanceof Long) {
            return (Long) obj;
        }
        return 0L;
    }

    public LanguageConfig getConfig(String language) {
        return languageConfigMap.get(language);
    }
}
