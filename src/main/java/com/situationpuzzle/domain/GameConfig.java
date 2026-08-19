package com.situationpuzzle.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "game_config")
public class GameConfig {

    @Id
    @Column(name = "config_key", length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue;

    @Column(length = 500)
    private String description;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
