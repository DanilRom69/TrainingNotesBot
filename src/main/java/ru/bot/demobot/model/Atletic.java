package ru.bot.demobot.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "atletic")
public class Atletic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private String atleticName;
    private String startName;
    private int distance;
    private Float time = 0f;
    private int restTime;

    private LocalDateTime createdAt = LocalDateTime.now();

}

