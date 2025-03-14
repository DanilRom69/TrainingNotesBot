package ru.bot.demobot.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "exercises")
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String exerciseName;
    private int weight;
    private int repetitions;
    private int setsCount = 1;
    private int totalWeight;
    private int restTime;

    private LocalDateTime createdAt = LocalDateTime.now();
}
