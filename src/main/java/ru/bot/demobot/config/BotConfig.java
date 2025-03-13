package ru.bot.demobot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
@Data
@Configuration
@PropertySource("application.properties")
public class BotConfig {
    @Value("${telegram.name}")
    String botName;
    @Value("${telegram.token}")
    String botToken;
}
