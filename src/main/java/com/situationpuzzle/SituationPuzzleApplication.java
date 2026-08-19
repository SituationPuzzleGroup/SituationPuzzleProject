package com.situationpuzzle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SituationPuzzleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SituationPuzzleApplication.class, args);
    }
}
