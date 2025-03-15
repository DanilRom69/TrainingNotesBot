package ru.bot.demobot.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@Entity
@Table(name = "body_parameters")
public class BodyParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatId;
    private LocalDateTime date;
    private int height;
    private int weight;
    private int biceps;
    private int chest;
    private int waist;
    private int hips;
    private int thighs;
    private int calves;
    private int shoulders;
    private int buttocks;

    public BodyParameters() {
        this.date = LocalDateTime.now();
    }


}
