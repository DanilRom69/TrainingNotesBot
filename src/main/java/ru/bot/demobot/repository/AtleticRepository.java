package ru.bot.demobot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bot.demobot.model.Atletic;
import ru.bot.demobot.model.User;

import java.util.List;
import java.util.Optional;

public interface AtleticRepository extends JpaRepository<Atletic, Long> {
    List<Atletic> findByChatIdAndAtleticNameAndDistance(long chatId, String atleticName, int distance);
    public List<Atletic> findByChatId(long chatId);
}