package com.situationpuzzle.config;

import com.situationpuzzle.service.game.HintLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.helper")
public class HelperProperties {
    private HintLevel defaultHintLevel = HintLevel.LOW;

    public HintLevel getDefaultHintLevel() { return defaultHintLevel; }
    public void setDefaultHintLevel(HintLevel defaultHintLevel) { this.defaultHintLevel = defaultHintLevel; }
}
