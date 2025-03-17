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
    private final Map<Long, Timer> restTimers = new HashMap<>(); // Храним таймеры отдыха

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
                        addNewSet(chatId,message, exercise); // Добавляем новый подход
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

    private void sendBodyParametersButtons(long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("Завершить");

        keyboardMarkup.setKeyboard(Collections.singletonList(row));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Для досрочного выхода нажмите Завершить");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void processBodyParametersInput(long chatId, String message) {
        if ("Завершить".equals(message)) {
            // Останавливаем процесс и удаляем текущие данные
            userState.remove(chatId);
            bodyParamsInput.remove(chatId);

            sendMessage(chatId, "Процесс ввода параметров тела был отменен.");
            sendMenuButtons(chatId); // Возвращаем в главное меню
            return;
        }

        BodyParameters params = bodyParamsInput.get(chatId);

        try {
            switch (userState.get(chatId)) {
                case "HEIGHT":
                    int height = Integer.parseInt(message);
                    if (height < 120 || height > 250) {
                        sendMessage(chatId, "Рост должен быть в пределах от 120 до 250 см.");
                        return;
                    }
                    params.setHeight(height);
                    userState.put(chatId, "WEIGHT");
                    sendMessage(chatId, "Введите ваш вес (в кг):");
                    break;

                case "WEIGHT":
                    int weight = Integer.parseInt(message);
                    if (weight < 30 || weight > 500) {
                        sendMessage(chatId, "Вес должен быть в пределах от 30 до 500 кг.");
                        return;
                    }
                    params.setWeight(weight);
                    userState.put(chatId, "BICEPS");
                    sendMessage(chatId, "Введите размер бицепса (в см):");
                    break;

                case "BICEPS":
                    int biceps = Integer.parseInt(message);
                    if (biceps < 10 || biceps > 100) {  // Примерное ограничение для бицепса
                        sendMessage(chatId, "Размер бицепса должен быть от 10 до 100 см.");
                        return;
                    }
                    params.setBiceps(biceps);
                    userState.put(chatId, "CHEST");
                    sendMessage(chatId, "Введите обхват груди (в см):");
                    break;

                case "CHEST":
                    int chest = Integer.parseInt(message);
                    if (chest < 50 || chest > 150) {  // Ограничение для груди
                        sendMessage(chatId, "Обхват груди должен быть от 50 до 150 см.");
                        return;
                    }
                    params.setChest(chest);
                    userState.put(chatId, "WAIST");
                    sendMessage(chatId, "Введите обхват талии (в см):");
                    break;

                case "WAIST":
                    int waist = Integer.parseInt(message);
                    if (waist < 50 || waist > 150) {  // Ограничение для талии
                        sendMessage(chatId, "Обхват талии должен быть от 50 до 150 см.");
                        return;
                    }
                    params.setWaist(waist);
                    userState.put(chatId, "HIPS");
                    sendMessage(chatId, "Введите обхват бедер (в см):");
                    break;

                case "HIPS":
                    int hips = Integer.parseInt(message);
                    if (hips < 50 || hips > 150) {  // Ограничение для бедер
                        sendMessage(chatId, "Обхват бедер должен быть от 50 до 150 см.");
                        return;
                    }
                    params.setHips(hips);
                    userState.put(chatId, "THIGHS");
                    sendMessage(chatId, "Введите обхват бедра (в см):");
                    break;

                case "THIGHS":
                    int thighs = Integer.parseInt(message);
                    if (thighs < 20 || thighs > 100) {  // Ограничение для бедра
                        sendMessage(chatId, "Обхват бедра должен быть от 20 до 100 см.");
                        return;
                    }
                    params.setThighs(thighs);
                    userState.put(chatId, "CALVES");
                    sendMessage(chatId, "Введите обхват икр (в см):");
                    break;

                case "CALVES":
                    int calves = Integer.parseInt(message);
                    if (calves < 20 || calves > 60) {  // Ограничение для икр
                        sendMessage(chatId, "Обхват икр должен быть от 20 до 60 см.");
                        return;
                    }
                    params.setCalves(calves);
                    userState.put(chatId, "SHOULDERS");
                    sendMessage(chatId, "Введите обхват плеч (в см):");
                    break;

                case "SHOULDERS":
                    int shoulders = Integer.parseInt(message);
                    if (shoulders < 30 || shoulders > 120) {  // Ограничение для плеч
                        sendMessage(chatId, "Обхват плеч должен быть от 30 до 120 см.");
                        return;
                    }
                    params.setShoulders(shoulders);
                    userState.put(chatId, "BUTTOCKS");
                    sendMessage(chatId, "Введите обхват ягодиц (в см):");
                    break;

                case "BUTTOCKS":
                    int buttocks = Integer.parseInt(message);
                    if (buttocks < 40 || buttocks > 130) {  // Ограничение для ягодиц
                        sendMessage(chatId, "Обхват ягодиц должен быть от 40 до 130 см.");
                        return;
                    }
                    params.setButtocks(buttocks);
                    params.setChatId(chatId);
                    bodyParametersRepository.save(params);

                    userState.remove(chatId);
                    bodyParamsInput.remove(chatId);

                    sendMessage(chatId, "✅ Ваши параметры успешно сохранены!");
                    sendMenuButtons(chatId);
                    break;
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Пожалуйста, введите число.");
        }
    }

    private void startBodyParametersInput(long chatId) {
        sendBodyParametersButtons(chatId);
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
        // Меняем клавиатуру на кнопки "Еще" и "Завершить"
        sendWorkoutButtons(chatId);
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
                int weight = Integer.parseInt(message); // Запись веса
                if (weight <= 0 || weight > 500) {
                    sendMessage(chatId, "Вес должен быть положительным и не более 500 кг.");
                    return;
                }
                exercise.setWeight(weight);
                askForRepetitions(chatId); // Переход к запросу повторений
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное число для веса.");
            }
        } else if (exercise.getRepetitions() == 0) {
            try {
                int repetitions = Integer.parseInt(message); // Запись количества повторений
                if (repetitions <= 0 || repetitions > 300) {
                    sendMessage(chatId, "Количество повторений должно быть положительным и не более 300.");
                    return;
                }
                exercise.setRepetitions(repetitions); // Записываем повторения
                askForRestTime(chatId); // Переход к запросу времени отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное количество повторений.");
            }
        } else if (exercise.getRestTime() == 0) {
            try {
                int restTime = Integer.parseInt(message); // Запись времени отдыха
                if (restTime <= 0 || restTime > 60) {
                    sendMessage(chatId, "Время отдыха должно быть положительным и не более 60 минут.");
                    return;
                }
                exercise.setRestTime(restTime); // Записываем время отдыха
                restTimes.put(chatId, restTime); // Сохраняем время отдыха
                saveExerciseSet(chatId, exercise); // Сохраняем первую запись в БД
                startRestTimeTimer(chatId, exercise); // Старт таймера отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное время отдыха.");
            }
        }
    }

    private void addNewSet(long chatId,String message, Exercise exercise) {
        // Создаем новую запись для нового подхода с тем же названием упражнения и временем отдыха
        if (restTimers.containsKey(chatId)) {
            restTimers.get(chatId).cancel();
            restTimers.remove(chatId);
        }
        Exercise newSet = new Exercise();
        newSet.setExerciseName(exercise.getExerciseName());


        // Переходим к запросу нового веса и повторений
        activeExercises.put(chatId, newSet);
        askForWeight(chatId);
        if  (exercise.getWeight() == 0) {
            try {
                int weight = Integer.parseInt(message); // Запись веса
                if (weight <= 0 || weight > 500) {
                    sendMessage(chatId, "Вес должен быть положительным и не более 500 кг.");
                    return;
                }
                exercise.setWeight(weight);
                askForRepetitions(chatId); // Переход к запросу повторений
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное число для веса.");
            }
        } else if (exercise.getRepetitions() == 0) {
            try {
                int repetitions = Integer.parseInt(message); // Запись количества повторений
                if (repetitions <= 0 || repetitions > 300) {
                    sendMessage(chatId, "Количество повторений должно быть положительным и не более 300.");
                    return;
                }
                exercise.setRepetitions(repetitions); // Записываем повторения
                askForRestTime(chatId); // Переход к запросу времени отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное количество повторений.");
            }
        } else if (exercise.getRestTime() == 0) {
            try {
                int restTime = Integer.parseInt(message); // Запись времени отдыха
                if (restTime <= 0 || restTime > 60) {
                    sendMessage(chatId, "Время отдыха должно быть положительным и не более 60 минут.");
                    return;
                }
                exercise.setRestTime(restTime); // Записываем время отдыха
                restTimes.put(chatId, restTime); // Сохраняем время отдыха
                saveExerciseSet(chatId, exercise); // Сохраняем первую запись в БД
                startRestTimeTimer(chatId, exercise); // Старт таймера отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "Введите корректное время отдыха.");
            }
        }

    }

    private void startRestTimeTimer(long chatId, Exercise exercise) {
        sendMessage(chatId, "Время отдыха началось, подождите...");

        Timer timer = new Timer();
        restTimers.put(chatId, timer); // Сохраняем таймер

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                restTimers.remove(chatId); // Удаляем таймер после завершения
                sendMessage(chatId, "Время отдыха прошло! Хотите сделать еще один подход?");
                askForAnotherSet(chatId);
            }
        }, exercise.getRestTime() * 60 * 1000); // Время отдыха в миллисекундах
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


    private void finishExercise(long chatId) {
        // Если есть активный таймер отдыха, отменяем его
        if (restTimers.containsKey(chatId)) {
            restTimers.get(chatId).cancel();
            restTimers.remove(chatId);
        }

        sendMessage(chatId, "Упражнение окончено!");
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
        message.setText("Выберите команду из меню");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendWorkoutButtons(long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("Еще");
        row.add("Завершить");

        keyboardMarkup.setKeyboard(Collections.singletonList(row));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Для завершения упражнения нажмите Завершить, для добавления подхода нажмите Еще. ");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(long chatId, String textToSend) {
        if (textToSend == null || textToSend.isEmpty()) {
            return  ; // или установите какое-то дефолтное сообщение
        }

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
