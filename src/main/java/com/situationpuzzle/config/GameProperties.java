package com.situationpuzzle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.game")
public class GameProperties {
    private int maxRounds = 4;
    private int defaultOptionScore = 20;
    private int normalEndingScore = 208;
    private int trueEndingScore = 256;
    private int defaultTruthThreshold = 60;

    public int getMaxRounds() { return maxRounds; }
    public void setMaxRounds(int maxRounds) { this.maxRounds = maxRounds; }
    public int getDefaultOptionScore() { return defaultOptionScore; }
    public void setDefaultOptionScore(int defaultOptionScore) { this.defaultOptionScore = defaultOptionScore; }
    public int getNormalEndingScore() { return normalEndingScore; }
    public void setNormalEndingScore(int normalEndingScore) { this.normalEndingScore = normalEndingScore; }
    public int getTrueEndingScore() { return trueEndingScore; }
    public void setTrueEndingScore(int trueEndingScore) { this.trueEndingScore = trueEndingScore; }
    public int getDefaultTruthThreshold() { return defaultTruthThreshold; }
    public void setDefaultTruthThreshold(int defaultTruthThreshold) { this.defaultTruthThreshold = defaultTruthThreshold; }
}
