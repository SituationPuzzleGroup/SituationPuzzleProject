package com.situationpuzzle.service.ai;

import com.situationpuzzle.domain.AiPromptTemplate;
import com.situationpuzzle.repository.AiPromptTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PromptTemplateService {
    private final AiPromptTemplateRepository repo;

    public PromptTemplateService(AiPromptTemplateRepository repo) {
        this.repo = repo;
    }

    public String render(String key, Map<String, String> vars, String fallback) {
        String template = repo.findByTemplateKeyAndEnabledTrue(key)
                .map(AiPromptTemplate::getContent)
                .orElse(fallback);
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        String out = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{{" + e.getKey() + "}}", v);
        }
        return out;
    }
}
