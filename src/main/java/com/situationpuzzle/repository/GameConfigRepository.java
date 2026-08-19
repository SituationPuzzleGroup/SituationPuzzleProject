package com.situationpuzzle.repository;

import com.situationpuzzle.domain.GameConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameConfigRepository extends JpaRepository<GameConfig, String> {
}
