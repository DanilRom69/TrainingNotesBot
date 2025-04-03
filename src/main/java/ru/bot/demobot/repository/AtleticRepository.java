package ru.bot.demobot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bot.demobot.model.Atletic;

import java.util.List;

public interface AtleticRepository extends JpaRepository<Atletic, Long> {
    List<Atletic> findByChatId(long chatId);
}