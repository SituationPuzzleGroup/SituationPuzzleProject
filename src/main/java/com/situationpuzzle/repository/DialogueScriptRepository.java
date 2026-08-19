package com.situationpuzzle.repository;

import com.situationpuzzle.domain.DialogueScript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DialogueScriptRepository extends JpaRepository<DialogueScript, Long> {
    Optional<DialogueScript> findByScriptKeyAndEnabledTrue(String scriptKey);
}
