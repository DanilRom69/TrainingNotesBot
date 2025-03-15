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
import ru.bot.demobot.model.BodyParameters;
import ru.bot.demobot.model.Exercise;
import ru.bot.demobot.model.User;
import ru.bot.demobot.repository.BodyParametersRepository;
import ru.bot.demobot.repository.ExerciseRepository;
import ru.bot.demobot.repository.UserRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private BodyParametersRepository bodyParametersRepository;

    // Хранение активных тренировок
    private final Map<Long, Exercise> activeExercises = new HashMap<>();
    // Хранение данных о времени отдыха для каждого пользователя
    private final Map<Long, Integer> restTimes = new HashMap<>();

    private final Map<Long, BodyParameters> bodyParamsInput = new HashMap<>();
    private final Map<Long, String> userState = new HashMap<>();

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

            if (userState.containsKey(chatId)) {
                processBodyParametersInput(chatId, message);
                return;
            }

            if (activeExercises.containsKey(chatId)) {
                Exercise exercise = activeExercises.get(chatId);

                switch (message) {
                    case "Завершить":
                        finishExercise(chatId);
                        break;

                    case "Еще":
                        addNewSet(chatId, exercise); // Добавляем новый подход
                        break;

                    default:
                        processInput(chatId, message, exercise);
                }
            } else {
                switch (message) {
                    case "/start":
                        startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                        sendMenuButtons(chatId);
                        break;
                    case "➕ Добавить тренировку":
                        addTraining(chatId);
                        break;
                    case "/help":
                        helpCommandReceived(chatId);
                        break;
                    case "/settings":
                        startBodyParametersInput(chatId);
                        break;
                    case "📋 Мои тренировки":
                        myTraining(chatId);
                        break;
                    case "📊 Статистика":
                        statisticsTraining(chatId);
                        break;
                    case "⚙ Параметры тела":
                        startBodyParametersInput(chatId);
                        break;
                    case "\uD83C\uDD98 Помощь":
                        helpCommandReceived(chatId);
                        break;
                    default:
                        sendMessage(chatId, "Такой команды нет, воспользуйтесь меню");
                }
            }
        }
    }

    private void processBodyParametersInput(long chatId, String message) {
        BodyParameters params = bodyParamsInput.get(chatId);

        try {
            switch (userState.get(chatId)) {
                case "HEIGHT":
                    params.setHeight(Integer.parseInt(message));
                    userState.put(chatId, "WEIGHT");
                    sendMessage(chatId, "Введите ваш вес (в кг):");
                    break;

                case "WEIGHT":
                    params.setWeight(Integer.parseInt(message));
                    userState.put(chatId, "BICEPS");
                    sendMessage(chatId, "Введите размер бицепса (в см):");
                    break;

                case "BICEPS":
                    params.setBiceps(Integer.parseInt(message));
                    userState.put(chatId, "CHEST");
                    sendMessage(chatId, "Введите обхват груди (в см):");
                    break;

                case "CHEST":
                    params.setChest(Integer.parseInt(message));
                    userState.put(chatId, "WAIST");
                    sendMessage(chatId, "Введите обхват талии (в см):");
                    break;

                case "WAIST":
                    params.setWaist(Integer.parseInt(message));
                    userState.put(chatId, "HIPS");
                    sendMessage(chatId, "Введите обхват бедер (в см):");
                    break;

                case "HIPS":
                    params.setHips(Integer.parseInt(message));
                    userState.put(chatId, "THIGHS");
                    sendMessage(chatId, "Введите обхват бедра (в см):");
                    break;

                case "THIGHS":
                    params.setThighs(Integer.parseInt(message));
                    userState.put(chatId, "CALVES");
                    sendMessage(chatId, "Введите обхват икр (в см):");
                    break;

                case "CALVES":
                    params.setCalves(Integer.parseInt(message));
                    userState.put(chatId, "SHOULDERS");
                    sendMessage(chatId, "Введите обхват плеч (в см):");
                    break;

                case "SHOULDERS":
                    params.setShoulders(Integer.parseInt(message));
                    userState.put(chatId, "BUTTOCKS");
                    sendMessage(chatId, "Введите обхват ягодиц (в см):");
                    break;

                case "BUTTOCKS":
                    params.setButtocks(Integer.parseInt(message));
                    params.setChatId(chatId);
                    bodyParametersRepository.save(params);

                    userState.remove(chatId);
                    bodyParamsInput.remove(chatId);

                    sendMessage(chatId, "✅ Ваши параметры успешно сохранены!");
                    break;
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Пожалуйста, введите число.");
        }
    }

    private void startBodyParametersInput(long chatId) {
        bodyParamsInput.put(chatId, new BodyParameters());
        userState.put(chatId, "HEIGHT");
        sendMessage(chatId, "Введите ваш рост (в см):");
    }

    private void statisticsTraining(long chatId) {
        List<BodyParameters> bodyParamsList = bodyParametersRepository.findByChatId(chatId);

        if (!bodyParamsList.isEmpty()) {
            StringBuilder formattedStats = new StringBuilder("📊 *Ваша статистика параметров тела:*\n\n");

            // Получаем первую и последнюю записи
            BodyParameters firstRecord = bodyParamsList.get(0);
            BodyParameters lastRecord = bodyParamsList.get(bodyParamsList.size() - 1);

            for (BodyParameters params : bodyParamsList) {
                formattedStats.append(String.format(
                        "📅 *Дата:* %s\n" +
                                "📏 *Рост:* %d см\n" +
                                "⚖ *Вес:* %d кг\n\n" +
                                "💪 *Бицепс:* %d см\n" +
                                "🏋️ *Грудь:* %d см\n" +
                                "🎯 *Талия:* %d см\n" +
                                "🍑 *Бёдра:* %d см\n" +
                                "🦵 *Бедро:* %d см\n" +
                                "🦶 *Икры:* %d см\n" +
                                "🏹 *Плечи:* %d см\n" +
                                "🍑 *Ягодицы:* %d см\n\n",
                        LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
                        params.getHeight(), params.getWeight(),
                        params.getBiceps(), params.getChest(), params.getWaist(),
                        params.getHips(), params.getThighs(), params.getCalves(),
                        params.getShoulders(), params.getButtocks()
                ));
                formattedStats.append("———————————————\n"); // Разделитель между записями
            }

            // Добавляем расчёт изменений
            formattedStats.append("📈 *Изменения параметров с первой записи:*\n\n");
            formattedStats.append(String.format(
                    "⚖ *Вес:* %d кг (%+d кг)\n" +
                            "💪 *Бицепс:* %d см (%+d см)\n" +
                            "🏋️ *Грудь:* %d см (%+d см)\n" +
                            "🎯 *Талия:* %d см (%+d см)\n" +
                            "🍑 *Бёдра:* %d см (%+d см)\n" +
                            "🦵 *Бедро:* %d см (%+d см)\n" +
                            "🦶 *Икры:* %d см (%+d см)\n" +
                            "🏹 *Плечи:* %d см (%+d см)\n" +
                            "🍑 *Ягодицы:* %d см (%+d см)\n",
                    lastRecord.getWeight(), lastRecord.getWeight() - firstRecord.getWeight(),
                    lastRecord.getBiceps(), lastRecord.getBiceps() - firstRecord.getBiceps(),
                    lastRecord.getChest(), lastRecord.getChest() - firstRecord.getChest(),
                    lastRecord.getWaist(), lastRecord.getWaist() - firstRecord.getWaist(),
                    lastRecord.getHips(), lastRecord.getHips() - firstRecord.getHips(),
                    lastRecord.getThighs(), lastRecord.getThighs() - firstRecord.getThighs(),
                    lastRecord.getCalves(), lastRecord.getCalves() - firstRecord.getCalves(),
                    lastRecord.getShoulders(), lastRecord.getShoulders() - firstRecord.getShoulders(),
                    lastRecord.getButtocks(), lastRecord.getButtocks() - firstRecord.getButtocks()
            ));

            sendMessage(chatId, formattedStats.toString());
        } else {
            sendMessage(chatId, "❌ У вас пока нет сохранённых параметров тела. Введите их в разделе \"⚙ Параметры тела\".");
        }
    }


    private void myTraining(long chatId) {
        List<Exercise> exercises = exerciseRepository.findByChatId(chatId);

        if (exercises.isEmpty()) {
            sendMessage(chatId, "У вас нет записанных тренировок.");
        } else {
            // Группируем тренировки по дате и упражнению
            Map<String, Map<String, List<Exercise>>> exercisesByDateAndName = exercises.stream()
                    .collect(Collectors.groupingBy(exercise -> exercise.getCreatedAt().toLocalDate().toString(),
                            Collectors.groupingBy(Exercise::getExerciseName)));

            // Форматируем дату
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");

            StringBuilder response = new StringBuilder("📝 *Ваши тренировки:*\n\n");

            // Проходим по каждой группе тренировок (по дате)
            for (Map.Entry<String, Map<String, List<Exercise>>> dateEntry : exercisesByDateAndName.entrySet()) {
                String date = dateEntry.getKey();
                Map<String, List<Exercise>> exercisesForDate = dateEntry.getValue();

                // Преобразуем строку даты в более читаемый формат
                LocalDate localDate = LocalDate.parse(date);
                String formattedDate = localDate.format(formatter);
                response.append("──────────────────────────\n");
                response.append("📅 *Дата:* ").append(formattedDate).append("\n");
                response.append("──────────────────────────\n");

                int totalDayWeight = 0; // Общий вес за весь день

                // Проходим по каждому упражнению за этот день
                for (Map.Entry<String, List<Exercise>> exerciseEntry : exercisesForDate.entrySet()) {
                    String exerciseName = exerciseEntry.getKey();
                    List<Exercise> exerciseList = exerciseEntry.getValue();

                    // Суммируем общий вес для одного упражнения за день
                    int totalWeightForExercise = exerciseList.stream()
                            .mapToInt(exercise -> exercise.getWeight() * exercise.getRepetitions())
                            .sum();

                    response.append("  \uD83E\uDD96 *Упражнение:* ").append(exerciseName).append("\n");

                    // Перечисляем вес и повторения каждого подхода для этого упражнения
                    for (Exercise exercise : exerciseList) {
                        response.append("    \uD83D\uDC1C Вес: ").append(exercise.getWeight()).append(" кг\n")
                                .append("    \uD83E\uDEBF Повторений: ").append(exercise.getRepetitions()).append("\n");
                    }

                    // Общий вес для этого упражнения за день
                    response.append("  \uD83E\uDD90 *Общий вес за упражнение:* ").append(totalWeightForExercise).append(" кг\n");
                    response.append("──────────────────────────\n");

                    // Добавляем общий вес этого упражнения к общему весу за день
                    totalDayWeight += totalWeightForExercise;
                }

                // Разделитель для следующей даты
                response.append("\n");
                response.append("==============================\n");
                response.append("   🏅 *Общий вес за день:* ").append(totalDayWeight).append(" кг\n");
                response.append("==============================\n");
            }

            sendMessage(chatId, response.toString());
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
                /start - Начать работу с ботом.
                \uD83C\uDD98 Помощь - Показать список команд.
                📋 Мои тренировки - Просмотр всех записанных вами тренировок с подробной статистикой.
                ➕ Добавить тренировку - Добавить новое упражнения, с регулируемым таймером тренировки.
                📊 Статистика - В данном разделе вы можете наблюдать за статистикой изменения параметров вашего тела.
                ⚙ Настройки параметров - Тут вы сможете заполнить свои параметры тела, для дальнейшего отслеживания статистики.
                
                🏋️ *Просто нажимай на кнопки, чтобы взаимодействовать с ботом!*""";
        sendMessage(chatId, answer);

    }

    private void addTraining(long chatId) {
        sendMessage(chatId, "Введите название упражнения:");

        // Создаем новое упражнение для активной тренировки
        Exercise exercise = new Exercise();
        activeExercises.put(chatId, exercise);
    }

    private void processInput(long chatId, String message, Exercise exercise) {
        if (exercise.getExerciseName() == null) {
            exercise.setExerciseName(message); // Запись названия упражнения
            askForWeight(chatId); // Переход к запросу веса
        } else if (exercise.getWeight() == 0) {
            try {
                exercise.setWeight(Integer.parseInt(message)); // Запись веса
                askForRepetitions(chatId); // Переход к запросу повторений
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное число для веса.");
            }
        } else if (exercise.getRepetitions() == 0) {
            try {
                exercise.setRepetitions(Integer.parseInt(message)); // Запись количества повторений
                askForRestTime(chatId); // Переход к запросу времени отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное количество повторений.");
            }
        } else if (exercise.getRestTime() == 0) {
            try {
                int restTime = Integer.parseInt(message); // Запись времени отдыха
                exercise.setRestTime(restTime);
                restTimes.put(chatId, restTime); // Сохраняем время отдыха
                saveExerciseSet(chatId, exercise); // Сохраняем первую запись в БД
                startRestTimeTimer(chatId, exercise); // Старт таймера отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректную команду Еще или Завершить");
            }
        }
    }

    private void startRestTimeTimer(long chatId, Exercise exercise) {
        sendMessage(chatId, "Время отдыха началось, подождите...");

        // Запускаем таймер для времени отдыха
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMessage(chatId, "Время отдыха прошло! Хотите сделать еще один подход?");
                askForAnotherSet(chatId);
            }
        }, exercise.getRestTime() * 60 * 1000); // Время отдыха в минутах
    }

    private void askForWeight(long chatId) {
        sendMessage(chatId, "Введите вес (кг):");
    }

    private void askForRepetitions(long chatId) {
        sendMessage(chatId, "Введите количество повторений:");
    }

    private void askForRestTime(long chatId) {
        sendMessage(chatId, "Введите время отдыха в минутах:");
    }

    private void askForAnotherSet(long chatId) {
        sendMessage(chatId, "Напишите 'Еще' для продолжения или 'Завершить' для завершения.");
    }

    private void addNewSet(long chatId, Exercise exercise) {
        // Создаем новую запись для нового подхода с тем же названием упражнения и временем отдыха
        Exercise newSet = new Exercise();
        newSet.setExerciseName(exercise.getExerciseName());
        newSet.setRestTime(exercise.getRestTime()); // Время отдыха сохраняем

        // Переходим к запросу нового веса и повторений
        activeExercises.put(chatId, newSet);
        askForWeight(chatId);
    }

    private void finishExercise(long chatId) {
        // Тренировка завершена, выходим в главное меню
        sendMessage(chatId, "Тренировка Окончена!");
        activeExercises.remove(chatId); // Очищаем активную тренировку для пользователя
        sendMenuButtons(chatId);
    }

    private void saveExerciseSet(long chatId, Exercise exercise) {
        // Сохраняем все подходы в базу данных
        Exercise savedExercise = new Exercise();
        savedExercise.setExerciseName(exercise.getExerciseName());
        savedExercise.setWeight(exercise.getWeight());
        savedExercise.setRepetitions(exercise.getRepetitions()); // Количество повторений
        savedExercise.setRestTime(exercise.getRestTime());
        savedExercise.setChatId(chatId);

        // Добавление записи в базу
        exerciseRepository.save(savedExercise);
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
        row2.add("⚙ Параметры тела");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("\uD83C\uDD98 Помощь");

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите команду.");
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
