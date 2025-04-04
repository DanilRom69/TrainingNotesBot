package ru.bot.demobot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import ru.bot.demobot.model.Exercise;

import java.time.LocalDateTime;
import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    List<Exercise> findByChatId(Long chatId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Exercise e WHERE e.chatId = :chatId AND e.createdAt BETWEEN :startOfDay AND :endOfDay")
    void deleteByChatIdAndDateRange(Long chatId, LocalDateTime startOfDay, LocalDateTime endOfDay);
}