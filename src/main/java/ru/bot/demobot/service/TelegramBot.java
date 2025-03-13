package ru.bot.demobot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.bot.demobot.config.BotConfig;
import ru.bot.demobot.model.User;
import ru.bot.demobot.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    @Autowired
    private UserRepository userRepository;


    public TelegramBot(BotConfig config) {
        this.config = config;
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            switch (message) {
                case "/start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    sendMenuButtons(chatId);
                    break;
                case "/help":
                    helpCommandReceived(chatId);
                    break;
                case "/settings":
                    settingsComandReceived(chatId);
                    break;
                case "📋 Мои тренировки":
                    myTraining(chatId);
                    break;
                case "➕ Добавить тренировку":
                    addTraining(chatId);
                    break;
                case "📊 Статистика":
                    statisticsTraining(chatId);
                    break;
                case "⚙ Настройки":
                    settingsComandReceived(chatId);
                    break;
                case "\uD83C\uDD98 Помощь":
                    helpCommandReceived(chatId);
                    break;

                default:
                    sendMessage(chatId, "sorry don't take command");
            }

        }

    }

    private void startCommandReceived(long chatId, String firstName) {

        userRepository.findByChatId(chatId).ifPresentOrElse(
                user -> sendMessage(chatId, "Вы уже зарегистрированы!"),
                () -> {
                    User newUser = new User();
                    newUser.setChatId(chatId);
                    newUser.setName(firstName);
                    userRepository.save(newUser);
                    sendMessage(chatId, "Вы успешно зарегистрированы!");
                }
        );
        String answer =
                "\uD83C\uDFCB\uFE0F\u200D♂\uFE0F Ваш личный фитнес-ассистент в Telegram! \uD83C\uDFCB\uFE0F\u200D♀\uFE0F\n" +
                        "\n" +
                        "Привет! " + firstName + "!\n" + " Я – твой персональный журнал тренировок. \uD83D\uDCD3\uD83D\uDCAA\n" +
                        "Сохраняй свои упражнения, следи за прогрессом и достигай новых высот! \uD83D\uDE80\n" +
                        "\n" +
                        "✨ Что я умею?\n" +
                        "✅ Фиксировать твои тренировки \uD83C\uDFCB\uFE0F\u200D♂\uFE0F\n" +
                        "✅ Запоминать вес, повторения и подходы" +
                        "✅ Отслеживать прогресс и мотивировать" +
                        "✅ Показывать историю тренировок \uD83D\uDCC5\n" +
                        "\n" +
                        "\uD83D\uDCA1 Просто введи данные о тренировке, и я сохраню их для тебя!";
        sendMessage(chatId, answer);

    }

    private void helpCommandReceived(long chatId) {
        String answer = """
                🤖 *Доступные команды:*
                /start - Начать работу с ботом
                \uD83C\uDD98 Помощь - Показать список команд
                📋 Мои тренировки - Просмотр сохраненных тренировок
                ➕ Добавить тренировку - Добавить новую тренировку
                📊 Статистика - Посмотреть статистику
                ⚙ Настройки - Настроить параметры бота
                
                🏋️ *Просто нажимай на кнопки, чтобы взаимодействовать с ботом!*""";
        sendMessage(chatId, answer);

    }

    private void settingsComandReceived(long chatId) {
        String answer = "Данное поле находится в разработке (настройки профиля)";
        sendMessage(chatId, answer);

    }

    private void statisticsTraining(long chatId) {
        String answer = "Данное поле находится в разработке (Статистика тренировок, некоторый журнал всего выполненного)";
        sendMessage(chatId, answer);
    }

    private void myTraining(long chatId) {
        String answer = "Данное поле находится в разработке (Список тренировок или сюда добавить программы тренировок)";
        sendMessage(chatId, answer);
    }

    private void addTraining(long chatId) {
        String answer = "Данное поле находится в разработке (Добавляем список упражнений подходы и т.п.)";
        sendMessage(chatId, answer);
    }


    private void sendMenuButtons(long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true); // Клавиатура адаптируется под экран
        keyboardMarkup.setOneTimeKeyboard(false); // Клавиатура остается открытой

        // Создаем кнопки
        KeyboardRow row1 = new KeyboardRow();
        row1.add("📋 Мои тренировки");
        row1.add("➕ Добавить тренировку");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("📊 Статистика");
        row2.add("⚙ Настройки");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("\uD83C\uDD98 Помощь");


        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Добро пожаловать! Выберите команду.");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(textToSend);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

}

