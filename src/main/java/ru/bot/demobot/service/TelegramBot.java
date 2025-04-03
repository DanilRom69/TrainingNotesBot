package ru.bot.demobot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.bot.demobot.config.BotConfig;
import ru.bot.demobot.model.Atletic;
import ru.bot.demobot.model.BodyParameters;
import ru.bot.demobot.model.Exercise;
import ru.bot.demobot.model.User;
import ru.bot.demobot.repository.BodyParametersRepository;
import ru.bot.demobot.repository.ExerciseRepository;
import ru.bot.demobot.repository.UserRepository;
import ru.bot.demobot.repository.AtleticRepository;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.File;
import java.io.IOException;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    final BotConfig config;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private AtleticRepository atleticRepository;

    @Autowired
    private BodyParametersRepository bodyParametersRepository;

    private final Map<Long, Exercise> activeExercises = new HashMap<>();// Хранение активных тренировок
    private final Map<Long, Atletic> activeAtletic = new HashMap<>();
    private final Map<Long, Integer> restTimes = new HashMap<>();// Хранение данных о времени отдыха для каждого пользователя
    private final Map<Long, Timer> restTimers = new HashMap<>(); // Храним таймеры отдыха
    private final Map<Long, BodyParameters> bodyParamsInput = new HashMap<>();
    private final Map<Long, String> userState = new HashMap<>();
    private final Map<Long, Integer> setCounters = new HashMap<>();

    public TelegramBot(BotConfig config) {
        this.config = config;
    }

    public String getBotToken() {
        return config.getBotToken();
    }

    public String getBotUsername() {
        return config.getBotName();
    }

    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            long chatId = callbackQuery.getMessage().getChatId();
            String callbackData = callbackQuery.getData();

            switch (callbackData) {
                case "start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName()); // Метод для отображения тренировок пользователя
                    break;
                case "myTrainings":
                    statisticHeavy(chatId);
                    ; // Метод для отображения тренировок пользователя
                    break;
                case "addExercise":
                    sendTrainingTypeSelection(chatId); // Метод для запроса на добавление упражнения
                    break;
                case "weight":
                    sendStrengthTrainingForm(chatId); // Метод для запроса на добавление упражнения
                    break;
                case "statistic":
                    sendStaticsticButtons(chatId); // Метод для отображения статистики
                    break;
                case "bodyParameters":
                    startBodyParametersInput(chatId); // Метод для отображения параметров тела
                    break;
                case "help":
                    helpCommandReceived(chatId); // Метод для отображения помощи
                    break;
                case "lightAtletic":
                    sendAthleticsTrainingForm(chatId); // Метод для добавления упражнения атлетики
                    break;
                case "atleticStatistic":
                    statisticAtletic(chatId); // Статистика атлетика
                    break;
                case "heavyStatistic":
                    myTraining(chatId);
                    myTrainingSred(chatId);
                    // Статистика работы с железом
                    break;
                case "bodyParametersStatistic":
                    statisticsTraining(chatId); // Статистика тела
                    break;
                default:
                    sendMessage(chatId, "Неизвестная команда.");
            }

            // Отправляем сообщение, чтобы бот не показал сообщение "This message was deleted"
            sendCallbackQueryAnswer(callbackQuery.getId());
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (userState.containsKey(chatId)) {
                String state = userState.get(chatId);

                switch (state) {
                    case "waiting_for_weight_last":
                        processLastSetWeight(chatId, message);
                        return;
                    case "waiting_for_reps_last":
                        processLastSetReps(chatId, message);
                        return;
                    default:
                        processBodyParametersInput(chatId, message);
                        return;
                }
            }


            if (activeAtletic.containsKey(chatId)) {
                Atletic atletic = activeAtletic.get(chatId);
                switch (message) {
                    case "Завершить":
                        finishAtletic(chatId);
                        break;
                    default:
                        processAtleticInput(chatId, message, atletic);
                }
                return;
            }

            if (activeExercises.containsKey(chatId)) {
                Exercise exercise = activeExercises.get(chatId);
                switch (message) {
                    case "Завершить":
                        finishExercise(chatId);
                        break;
                    case "Еще":
                        addNewSet(chatId, message, exercise);
                    case "Ещё":
                        addNewSet(chatId, message, exercise);
                        break;// Добавляем новый подход
                    case "Последний подход":
                        lastSet(chatId, exercise);
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
                    case "/addexercise":
                        sendTrainingTypeSelection(chatId);
                        break;
                    case "/lightatletic":
                        // Если выбрал "Легкая атлетика", открываем форму для легкой атлетики
                        sendAthleticsTrainingForm(chatId);
                        break;
                    case "/weight":
                        // Если выбрал "Работа с железом", открываем форму для работы с железом
                        sendStrengthTrainingForm(chatId);
                        break;
                    case "/help":
                        helpCommandReceived(chatId);
                        break;
                    case "/bodyparameters":
                        startBodyParametersInput(chatId);
                        break;
                    case "/mytrainings":
                        statisticHeavy(chatId);
                        break;
                    case "/statistic":
                        sendStaticsticButtons(chatId);
                        break;
                    case "/bodyparametersstatistic":
                        statisticsTraining(chatId);
                        break;
                    case "/heavystatistic":
                        myTraining(chatId);
                        break;
                    case "/atleticstatistic":
                        statisticAtletic(chatId);
                        break;
                    default:
                        sendMessage(chatId, "Такой команды нет, воспользуйтесь меню");
                }
            }

        }

    }

    private void sendCallbackQueryAnswer(String callbackQueryId) {
        try {
            // Отправляем ответ, чтобы сообщение не показало "This message was deleted"
            AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
            answerCallbackQuery.setCallbackQueryId(callbackQueryId);
            answerCallbackQuery.setText("Ваш запрос обработан.");
            answerCallbackQuery.setShowAlert(false);  // Не показывать алерт
            execute(answerCallbackQuery);
        } catch (TelegramApiException e) {
            e.printStackTrace();
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

    private void sendTrainingTypeSelection(long chatId) {


        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton button1 = new InlineKeyboardButton("Легкая атлетика");
        button1.setCallbackData("lightAtletic");
        row1.add(button1);

        InlineKeyboardButton button2 = new InlineKeyboardButton("Работа с железом");
        button2.setCallbackData("weight");
        row1.add(button2);


        rows.add(row1);
        keyboardMarkup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберите тип упражнения");
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
            removeReplyKeyboard(chatId, "Клавиатура убрана");
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
                    if (weight < 30 || weight > 800) {
                        sendMessage(chatId, "Вес должен быть в пределах от 30 до 800 кг.");
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
                        params.getDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
                        params.getHeight(), params.getWeight(), params.getBiceps(),
                        params.getChest(), params.getWaist(), params.getHips(),
                        params.getThighs(), params.getCalves(), params.getShoulders(),
                        params.getButtocks()
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

            // Создаем график
            File chartImage = generateBodyParametersChart(bodyParamsList);

            if (chartImage != null) {
                sendPhoto(chatId, chartImage, "📉 График изменений параметров тела");
            }
        } else {
            sendMessage(chatId, "❌ У вас пока нет сохранённых параметров тела. Введите их в разделе \"⚙ Параметры тела\".");
        }

        sendMenuButtons(chatId);
    }

    private void myTrainingSred(long chatId) {
        List<Exercise> exercises = exerciseRepository.findByChatId(chatId);
        if (exercises.isEmpty()) {
            sendMessage(chatId, "У вас нет записанных тренировок.");
            return;
        }

        Map<String, Map<String, Double>> exerciseData = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");

        Map<LocalDate, List<Exercise>> exercisesByDate = exercises.stream()
                .collect(Collectors.groupingBy(ex -> ex.getCreatedAt().toLocalDate()));

        for (Map.Entry<LocalDate, List<Exercise>> entry : exercisesByDate.entrySet()) {
            LocalDate date = entry.getKey();
            String formattedDate = date.format(formatter);
            Map<String, List<Exercise>> exercisesByName = entry.getValue().stream()
                    .collect(Collectors.groupingBy(Exercise::getExerciseName));

            for (Map.Entry<String, List<Exercise>> exerciseEntry : exercisesByName.entrySet()) {
                String exerciseName = exerciseEntry.getKey();
                List<Exercise> exerciseList = exerciseEntry.getValue();

                int totalWeight = 0;
                int totalReps = 0;

                for (Exercise exercise : exerciseList) {
                    totalWeight += exercise.getWeight() * exercise.getRepetitions();
                    totalReps += exercise.getRepetitions();
                }

                double averageWeight = totalReps > 0 ? (double) totalWeight / totalReps : 0;
                exerciseData.putIfAbsent(exerciseName, new LinkedHashMap<>());
                exerciseData.get(exerciseName).put(formattedDate, averageWeight);
            }
        }

        generateWeightGraph(chatId, exerciseData);
        sendMenuButtons(chatId);
    }

    private void generateWeightGraph(long chatId, Map<String, Map<String, Double>> exerciseData) {
        if (exerciseData.isEmpty()) {
            sendMessage(chatId, "Нет данных для построения графика. \uD83D\uDCCA");
            return;
        }

        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Map<String, Double>> entry : exerciseData.entrySet()) {
            String exerciseName = entry.getKey();
            for (Map.Entry<String, Double> dataPoint : entry.getValue().entrySet()) {
                dataset.addValue(dataPoint.getValue(), exerciseName, dataPoint.getKey());
            }
        }

        JFreeChart lineChart = ChartFactory.createLineChart(
                "Динамика среднего веса по упражнениям",
                "Дата",
                "Средний вес (кг)",
                dataset
        );
        lineChart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));

        File chartFile = new File("weight_chart.png");
        try {
            ChartUtils.saveChartAsPNG(chartFile, lineChart, 800, 600);
            sendPhoto(chatId, chartFile, "\uD83D\uDCCA Динамика среднего веса по упражнениям");
        } catch (IOException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при создании графика. \uD83D\uDCCA");
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

            // Проходим по каждой группе тренировок (по дате)
            for (Map.Entry<String, Map<String, List<Exercise>>> dateEntry : exercisesByDateAndName.entrySet()) {
                String date = dateEntry.getKey();
                Map<String, List<Exercise>> exercisesForDate = dateEntry.getValue();

                // Преобразуем строку даты в более читаемый формат
                LocalDate localDate = LocalDate.parse(date);
                String formattedDate = localDate.format(formatter);

                StringBuilder response = new StringBuilder("📅 *Дата:* ").append(formattedDate).append("\n");

                int totalDayWeight = 0; // Общий вес за весь день

                // Проходим по каждому упражнению за этот день
                for (Map.Entry<String, List<Exercise>> exerciseEntry : exercisesForDate.entrySet()) {
                    String exerciseName = exerciseEntry.getKey();
                    List<Exercise> exerciseList = exerciseEntry.getValue();

                    // Группируем повторения по весу
                    Map<Integer, List<Integer>> weightRepsMap = exerciseList.stream()
                            .collect(Collectors.groupingBy(Exercise::getWeight, Collectors.mapping(Exercise::getRepetitions, Collectors.toList())));

                    response.append("\n\uD83E\uDD96 *Упражнение:* ").append(exerciseName).append("\n");

                    // Перечисляем вес и повторения
                    for (Map.Entry<Integer, List<Integer>> weightEntry : weightRepsMap.entrySet()) {
                        int weight = weightEntry.getKey();
                        List<Integer> repetitions = weightEntry.getValue();

                        // Форматируем повторения через запятую
                        String repsString = repetitions.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(", "));

                        response.append("  \uD83D\uDC1C Вес: ").append(weight).append(" кг\n")
                                .append("  \uD83E\uDEBF Повторений: ").append(repsString).append("\n");
                    }

                    // Суммируем общий вес для одного упражнения за день
                    int totalWeightForExercise = exerciseList.stream().mapToInt(exercise -> exercise.getWeight() * exercise.getRepetitions()).sum();
                    response.append("  \uD83E\uDD90 *Общий вес за упражнение:* ").append(totalWeightForExercise).append(" кг\n");

                    // Добавляем общий вес этого упражнения к общему весу за день
                    totalDayWeight += totalWeightForExercise;
                }

                // Разделитель и отправка сообщения для каждого дня
                response.append("\n==============================\n")
                        .append("🏅 *Общий вес за день:* ").append(totalDayWeight).append(" кг\n")
                        .append("==============================\n");

                // Отправляем сообщение с тренировками за день
                sendMessage(chatId, response.toString());
            }
        }
    }

    private void startCommandReceived(long chatId, String firstName) {
        userRepository.findByChatId(chatId).ifPresentOrElse(user -> sendMessage(chatId, "Вы уже зарегистрированы!"), () -> {
            User newUser = new User();
            newUser.setChatId(chatId);
            newUser.setName(firstName);
            userRepository.save(newUser);
            sendMessage(chatId, "Вы успешно зарегистрированы!");
        });
        String answer = "\uD83C\uDFCB\uFE0F\u200D♂\uFE0F Ваш личный фитнес-ассистент в Telegram! \uD83C\uDFCB\uFE0F\u200D♀\uFE0F\n" + "\n" + "Привет! " + firstName + "!\n" + " Я – твой персональный журнал тренировок. \uD83D\uDCD3\uD83D\uDCAA\n" + "Сохраняй свои упражнения, следи за прогрессом и достигай новых высот! \uD83D\uDE80\n" + "\n" + "✨ Что я умею?\n" + "✅ Фиксировать твои тренировки \uD83C\uDFCB\uFE0F\u200D♂\uFE0F\n" + "✅ Запоминать вес, повторения и подходы" + "✅ Отслеживать прогресс и мотивировать" + "✅ Показывать историю тренировок \uD83D\uDCC5\n" + "\n" + "\uD83D\uDCA1 Просто введи данные о тренировке, и я сохраню их для тебя!";
        sendMessage(chatId, answer);
    }

    private void helpCommandReceived(long chatId) {
        String answer = """
                🤖 *Доступные команды:*
                /start - Начать работу с ботом.
                \uD83C\uDD98 Помощь - Показать список команд.
                📋 Мои тренировки - Просмотр всех записанных вами тренировок с подробной статистикой.
                ➕ Добавить Упражнение - Добавить новое упражнения, с регулируемым таймером тренировки.
                📊 Статистика - В данном разделе вы можете наблюдать за статистикой изменения параметров вашего тела.
                ⚙ Настройки параметров - Тут вы сможете заполнить свои параметры тела, для дальнейшего отслеживания статистики.
                
                🏋️ *Просто нажимай на кнопки, чтобы взаимодействовать с ботом!*""";
        sendMessage(chatId, answer);

    }

    private void sendStrengthTrainingForm(long chatId) {
        // Меняем клавиатуру на кнопку "Завершить"
        sendWorkoutButtons2(chatId);
        sendMessage(chatId, "\uD83C\uDFCB\uFE0F\u200D♀\uFE0F Введите название упражнения:");

        // Создаем новое упражнение для активной тренировки
        Exercise exercise = new Exercise();
        activeExercises.put(chatId, exercise);

    }

    private void sendAthleticsTrainingForm(long chatId) {
        Atletic atletic = new Atletic();
        activeAtletic.put(chatId, atletic);
        sendAtleticButtons(chatId);
        sendMessage(chatId, "\uD83C\uDFC3\u200D♂\uFE0F Введите название атлетического упражнения");
    }

    private void processAtleticInput(long chatId, String message, Atletic atletic) {
        try {
            if (atletic.getAtleticName() == null) {
                atletic.setAtleticName(message);
                askAtleticDistance(chatId);
                return;
            }

            if (atletic.getDistance() == 0) {
                try {
                    int distance = Integer.parseInt(message);
                    if (distance <= 0 || distance > 10000) {
                        return;
                    }
                    atletic.setDistance(distance);
                    askAtleticTime(chatId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "⚠ Введите корректное число для дистанции.");
                }
                return;
            }

            if (atletic.getTime() == 0.0f) {
                try {
                    // Меняем запятую на точку для корректного преобразования
                    float time = Float.parseFloat(message.replace(",", "."));
                    time = (float) (Math.round(time * 1000.0) / 1000.0);

                    if (time <= 0 || time > 10000) {
                        sendMessage(chatId, "❌ Время должно быть больше 0 и не больше 10000 секунд.");
                        return;
                    }
                    atletic.setTime(time);
                    saveExerciseSetAtletic(chatId, atletic);
                    sendMenuButtons(chatId);// Сохраняем данные
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "❌ Введите корректное число для времени, например: 12.5 или 60");
                }
            }

        } catch (Exception e) {
            sendMessage(chatId, "❌ Произошла ошибка при вводе данных. Попробуйте ещё раз.");
            e.printStackTrace();  // Логируем ошибку в консоль
        }
    }

    private void saveExerciseSetAtletic(long chatId, Atletic atletic) {
        try {
            Atletic saveAtletic = new Atletic();

            saveAtletic.setAtleticName(atletic.getAtleticName());
            saveAtletic.setDistance(atletic.getDistance());
            saveAtletic.setTime(atletic.getTime());  // Передаем уже проверенное значение
            saveAtletic.setChatId(chatId);

            atleticRepository.save(saveAtletic);  // Сохраняем в базу данных

            activeAtletic.remove(chatId);  // Удаляем пользователя из списка активных тренировок
            sendMessage(chatId, "✅ Тренировка по лёгкой атлетике успешно сохранена!");
            removeReplyKeyboard(chatId, "✅ Клавиатура убрана");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка сохранения данных. Попробуйте ещё раз.");
            e.printStackTrace();  // Выводим ошибку в логи (для отладки)
        }
    }

    private void askAtleticDistance(long chatId) {
        sendMessage(chatId, "\uD83D\uDCDD Введите какая дистанция в метрах");
    }

    private void askAtleticTime(long chatId) {
        sendMessage(chatId, "\uD83D\uDCDD Введите время (секунды), например: 12.5 или 60.");
    }

    private void processInput(long chatId, String message, Exercise exercise) {
        if (exercise.getExerciseName() == null) {
            exercise.setExerciseName(message); // Запись названия упражнения
            askForWeight(chatId); // Переход к запросу веса
        } else if (exercise.getWeight() == 0) {
            try {
                int weight = Integer.parseInt(message); // Запись веса
                if (weight <= 0 || weight > 500) {
                    sendMessage(chatId, "❌ Вес должен быть положительным и не более 500 кг.");
                    return;
                }
                exercise.setWeight(weight);
                askForRepetitions(chatId); // Переход к запросу повторений
            } catch (NumberFormatException e) {
                sendMessage(chatId, "\uD83D\uDCDD Введите корректное число для веса.");
            }
        } else if (exercise.getRepetitions() == 0) {
            try {
                int repetitions = Integer.parseInt(message); // Запись количества повторений
                if (repetitions <= 0 || repetitions > 300) {
                    sendMessage(chatId, "❌ Количество повторений должно быть положительным и не более 300.");
                    return;
                }
                exercise.setRepetitions(repetitions); // Записываем повторения
                askForRestTime(chatId); // Переход к запросу времени отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "\uD83D\uDCDD Введите корректное количество повторений.");
            }
        } else if (exercise.getRestTime() == 0) {
            try {
                int restTime = Integer.parseInt(message); // Запись времени отдыха
                if (restTime <= 0 || restTime > 60) {
                    sendMessage(chatId, "❌ Время отдыха должно быть положительным и не более 60 минут.");
                    return;
                }
                exercise.setRestTime(restTime); // Записываем время отдыха
                restTimes.put(chatId, restTime); // Сохраняем время отдыха
                saveExerciseSet(chatId, exercise); // Сохраняем первую запись в БД
                startRestTimeTimer(chatId, exercise); // Старт таймера отдыха
            } catch (NumberFormatException e) {
                sendMessage(chatId, "\uD83D\uDCDD Введите корректное время отдыха.");
            }
        }
    }

    private void addNewSet(long chatId, String message, Exercise exercise) {
        setCounters.put(chatId, setCounters.getOrDefault(chatId, 1) + 1);
        int currentSet = setCounters.get(chatId);

        // Отправляем сообщение пользователю
        sendMessage(chatId, "Подход №" + currentSet);

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
        if (exercise.getWeight() == 0) {
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
        sendWorkoutButtons(chatId);
        sendMessage(chatId, "⏱ Время отдыха началось, подождите...");
        Timer timer = new Timer();
        restTimers.put(chatId, timer); // Сохраняем таймер

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                restTimers.remove(chatId); // Удаляем таймер после завершения
                sendMessage(chatId, "\uD83D\uDCA1 Время отдыха прошло! Хотите сделать ещё один подход?");
            }
        }, exercise.getRestTime() * 60 * 1000); // Время отдыха в миллисекундах
    }

    private void askForWeight(long chatId) {
        sendMessage(chatId, "\uD83D\uDCDD Введите вес (кг):");
    }

    private void askForRepetitions(long chatId) {
        sendMessage(chatId, "\uD83D\uDCDD Введите количество повторений:");
    }

    private void askForRestTime(long chatId) {
        sendMessage(chatId, "\uD83D\uDCDD Введите время отдыха в минутах:");
    }

    private void finishAtletic(long chatId) {
        sendMessage(chatId, "✅ Упражнение окончено!");
        activeAtletic.remove(chatId);
        removeReplyKeyboard(chatId, "✅ Клавиатура убрана");
        sendMenuButtons(chatId);
    }

    private void finishExercise(long chatId) {
        // Если есть активный таймер отдыха, отменяем его
        if (restTimers.containsKey(chatId)) {
            restTimers.get(chatId).cancel();
            restTimers.remove(chatId);
            setCounters.remove(chatId);
        }

        sendMessage(chatId, "✅ Упражнение окончено!");
        activeExercises.remove(chatId);
        removeReplyKeyboard(chatId, "✅ Клавиатура убрана");// Очищаем активную тренировку для пользователя
        sendMenuButtons(chatId);
    }

    private void saveExerciseSet(long chatId, Exercise exercise) {
        // Проверяем, задано ли название упражнения
        if (exercise.getExerciseName() == null || exercise.getExerciseName().isEmpty()) {
            sendMessage(chatId, "⚠ Ошибка: Название упражнение не задано");
            return;
        }

        try {
            Exercise savedExercise = new Exercise();
            savedExercise.setChatId(chatId);
            savedExercise.setExerciseName(exercise.getExerciseName());
            savedExercise.setWeight(exercise.getWeight());
            savedExercise.setRepetitions(exercise.getRepetitions());
            savedExercise.setRestTime(exercise.getRestTime());
            savedExercise.setSetsCount(exercise.getSetsCount() > 0 ? exercise.getSetsCount() : 1); // По умолчанию 1 подход
            savedExercise.setCreatedAt(LocalDateTime.now()); // Устанавливаем текущую дату и время

            // Добавление записи в базу
            exerciseRepository.save(savedExercise);
            sendMessage(chatId, "✅ Подход сохранён: " + savedExercise.getExerciseName() +
                    "\n | Вес: " + savedExercise.getWeight() + " кг \n | Повторения: " + savedExercise.getRepetitions());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка при сохранении подхода. Попробуйте ещё раз.");
            e.printStackTrace();
        }
    }

    private void sendMenuButtons(long chatId) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton button1 = new InlineKeyboardButton("📋 Мои тренировки");
        button1.setCallbackData("myTrainings");
        row1.add(button1);

        InlineKeyboardButton button2 = new InlineKeyboardButton("➕ Добавить упражнение");
        button2.setCallbackData("addExercise");
        row1.add(button2);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton button3 = new InlineKeyboardButton("📊 Статистика");
        button3.setCallbackData("statistic");
        row2.add(button3);

        InlineKeyboardButton button4 = new InlineKeyboardButton("⚙ Параметры тела");
        button4.setCallbackData("bodyParameters");
        row2.add(button4);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton button5 = new InlineKeyboardButton("\uD83C\uDD98 Помощь");
        button5.setCallbackData("help");
        row3.add(button5);

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        keyboardMarkup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83D\uDCCD Главное меню:");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendAtleticButtons(long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("Завершить");
        keyboardMarkup.setKeyboard(Collections.singletonList(row));
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83D\uDCCC Для завершения упражнения нажмите Завершить");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void removeReplyKeyboard(long chatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);

        // Удаляем клавиатуру
        ReplyKeyboardRemove keyboardRemove = new ReplyKeyboardRemove();
        keyboardRemove.setRemoveKeyboard(true);
        message.setReplyMarkup(keyboardRemove);

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
        row.add("Ещё");
        row.add("Последний подход");
        KeyboardRow row2 = new KeyboardRow();
        row2.add("Завершить");

        keyboardMarkup.setKeyboard(Arrays.asList(row,row2));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83D\uDCCC Выберите действие после таймера: Добавить подход, последний подход без отдыха или завершить упражнение.");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendWorkoutButtons2(long chatId) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);

        KeyboardRow row = new KeyboardRow();
        row.add("Завершить");

        keyboardMarkup.setKeyboard(Arrays.asList(row));

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("\uD83D\uDCCC Выберите действие: Завершить упражнение все не сохраненные данные будут утеряны.");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void lastSet(long chatId, Exercise exercise) {
        // 1. Останавливаем таймер, если он активен

        if (restTimers.containsKey(chatId)) {
            restTimers.get(chatId).cancel();
            restTimers.remove(chatId);
        }

        setCounters.put(chatId, setCounters.getOrDefault(chatId, 1) + 1);
        int currentSet = setCounters.get(chatId);

        sendMessage(chatId, "\uD83D\uDE24 Последний подход! № "+currentSet+"\n\uD83D\uDCDD Введите вес:");

        // 2. Устанавливаем состояние для ожидания веса
        userState.put(chatId, "waiting_for_weight_last");

        // 3. Копируем название упражнения
        Exercise lastExercise = new Exercise();
        lastExercise.setExerciseName(exercise.getExerciseName());

        activeExercises.put(chatId, lastExercise);
    }

    private void processLastSetWeight(long chatId, String message) {
        try {
            int weight = Integer.parseInt(message);
            if (weight <= 0 || weight > 500) {
                sendMessage(chatId, "Вес должен быть положительным и не более 500 кг.");
                return;
            }

            Exercise exercise = activeExercises.get(chatId);
            exercise.setWeight(weight);

            sendMessage(chatId, "Введите количество повторений:");
            userState.put(chatId, "waiting_for_reps_last");
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Введите корректное число для веса.");
        }
    }

    private void processLastSetReps(long chatId, String message) {
        try {
            int reps = Integer.parseInt(message);
            if (reps <= 0 || reps > 300) {
                sendMessage(chatId, "Количество повторений должно быть положительным и не более 300.");
                return;
            }

            Exercise exercise = activeExercises.get(chatId);
            exercise.setRepetitions(reps);

            // 4. Записываем подход в БД
            saveExerciseSet(chatId, exercise);

            // 5. Завершаем упражнение и выводим в меню
            finishExercise(chatId);

            // Удаляем состояние пользователя
            userState.remove(chatId);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Введите корректное количество повторений.");
        }
    }

    private void statisticAtletic(long chatId) {
        List<Atletic> atletics = atleticRepository.findByChatId(chatId);

        if (atletics.isEmpty()) {
            sendMessage(chatId, "❌ У вас ещё нет сохраненных тренировок.");
            return;
        }

        // Группируем тренировки по дате, названию упражнения и дистанции
        Map<String, Map<String, Map<Integer, List<Atletic>>>> atleticsByDateAndNameAndDistance = atletics.stream()
                .collect(Collectors.groupingBy(atletic -> atletic.getCreatedAt().toLocalDate().toString(),
                        Collectors.groupingBy(Atletic::getAtleticName,
                                Collectors.groupingBy(Atletic::getDistance))));

        // Форматируем дату
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");

        // Данные для графика
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // Проходим по каждой группе тренировок (по дате)
        for (Map.Entry<String, Map<String, Map<Integer, List<Atletic>>>> dateEntry : atleticsByDateAndNameAndDistance.entrySet()) {
            String date = dateEntry.getKey();
            Map<String, Map<Integer, List<Atletic>>> atleticsForDate = dateEntry.getValue();

            // Преобразуем строку даты в более читаемый формат
            LocalDate localDate = LocalDate.parse(date);
            String formattedDate = localDate.format(formatter);

            StringBuilder response = new StringBuilder("📅 *Дата:* ").append(formattedDate).append("\n");

            // Проходим по каждому упражнению за этот день
            for (Map.Entry<String, Map<Integer, List<Atletic>>> atleticEntry : atleticsForDate.entrySet()) {
                String atleticName = atleticEntry.getKey();
                Map<Integer, List<Atletic>> atleticListByDistance = atleticEntry.getValue();

                response.append("\n🏃‍♂️ *Упражнение:* ").append(atleticName).append("\n");

                // Проходим по каждой дистанции для этого упражнения
                for (Map.Entry<Integer, List<Atletic>> distanceEntry : atleticListByDistance.entrySet()) {
                    int distance = distanceEntry.getKey();
                    List<Atletic> atleticList = distanceEntry.getValue();

                    // Формируем строку времени для одного упражнения с одинаковыми дистанциями
                    String times = atleticList.stream()
                            .map(atletic -> String.format("%.2f сек", atletic.getTime()))
                            .collect(Collectors.joining(", "));

                    response.append("📏 *Дистанция:* ").append(distance).append(" м\n")
                            .append("⏱ *Время:* ").append(times).append("\n");

                    // Добавляем данные для графика (добавляем в график время для каждой дистанции и дня)
                    for (Atletic atletic : atleticList) {
                        dataset.addValue(atletic.getTime(), atleticName + " " + distance + "м", formattedDate);
                    }
                }
            }

            // Разделитель и отправка сообщения для каждого дня
            response.append("\n==============================\n");

            // Отправляем сообщение с тренировками за день
            sendMessage(chatId, response.toString());
        }
        // Создаем и отправляем график
        File chartFile = createAtleticChart(dataset);
        if (chartFile != null) {
            sendPhoto(chatId, chartFile, "Ваш прогресс на графике \uD83D\uDCCA");
        }
        sendMenuButtons(chatId);
    }

    private File createAtleticChart(DefaultCategoryDataset dataset) {
        if (dataset.getColumnCount() == 0) {
            return null;
        }

        JFreeChart lineChart = ChartFactory.createLineChart(
                "Динамика времени по тренировкам",
                "Дата",
                "Время (сек)",
                dataset
        );
        lineChart.getTitle().setFont(new Font("Arial", Font.BOLD, 16));

        // Сохраняем график в файл
        File chartFile = new File("atletic_chart.png");
        try {
            ChartUtils.saveChartAsPNG(chartFile, lineChart, 800, 600);
            return chartFile;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private File generateBodyParametersChart(List<BodyParameters> bodyParamsList) {
        try {
            XYSeries weightSeries = new XYSeries("Вес");
            XYSeries bicepsSeries = new XYSeries("Бицепс");
            XYSeries chestSeries = new XYSeries("Грудь");
            XYSeries waistSeries = new XYSeries("Талия");

            int index = 1;
            for (BodyParameters params : bodyParamsList) {
                weightSeries.add(index, params.getWeight());
                bicepsSeries.add(index, params.getBiceps());
                chestSeries.add(index, params.getChest());
                waistSeries.add(index, params.getWaist());
                index++;
            }

            XYSeriesCollection dataset = new XYSeriesCollection();
            dataset.addSeries(weightSeries);
            dataset.addSeries(bicepsSeries);
            dataset.addSeries(chestSeries);
            dataset.addSeries(waistSeries);

            JFreeChart chart = ChartFactory.createXYLineChart(
                    "Изменение параметров тела",
                    "Измерение",
                    "Значение",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false
            );

            File chartFile = new File("body_parameters_chart.png");
            ChartUtils.saveChartAsPNG(chartFile, chart, 800, 600);
            return chartFile;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendPhoto(long chatId, File photo, String caption) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(photo));
        sendPhoto.setCaption(caption);

        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void statisticHeavy(long chatId) {
        sendMessage(chatId, "\uD83D\uDED1 Данный раздел в разработке");
    }

    private void sendStaticsticButtons(long chatId) {

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton button1 = new InlineKeyboardButton("Атлетика");
        button1.setCallbackData("atleticStatistic");
        row1.add(button1);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton button2 = new InlineKeyboardButton("Работа с железом");
        button2.setCallbackData("heavyStatistic");
        row2.add(button2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton button3 = new InlineKeyboardButton("Тело");
        button3.setCallbackData("bodyParametersStatistic");
        row3.add(button3);

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        keyboardMarkup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Выберет интересующую вас статистику");
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

    private void sendMessage(long chatId, String textToSend) {
        if (textToSend == null || textToSend.isEmpty()) {
            return; // или установите какое-то дефолтное сообщение
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
