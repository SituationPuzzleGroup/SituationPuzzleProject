package com.situationpuzzle.repository;

import com.situationpuzzle.domain.StoryOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoryOptionRepository extends JpaRepository<StoryOption, Long> {
    List<StoryOption> findByStoryIdAndEnabledTrueOrderBySortOrderAsc(Long storyId);
    Optional<StoryOption> findByIdAndStoryIdAndEnabledTrue(Long id, Long storyId);
}
