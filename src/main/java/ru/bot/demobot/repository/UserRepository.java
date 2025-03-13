package ru.bot.demobot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.bot.demobot.model.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByChatId(Long chatId);
}
