package com.situationpuzzle.repository;

import com.situationpuzzle.domain.Story;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {
    Optional<Story> findByStoryOrder(Integer storyOrder);
    List<Story> findByEnabledTrueOrderByStoryOrderAsc();
}
