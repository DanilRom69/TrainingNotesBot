package ru.bot.demobot.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import ru.bot.demobot.model.BodyParameters;

import java.util.List;

public interface BodyParametersRepository extends JpaRepository<BodyParameters, Long> {
    List<BodyParameters> findByChatId(Long chatId);
}
