package ru.bot.demobot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bot.demobot.model.Exercise;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findByChatId(Long chatId);
}
