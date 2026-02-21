package com.example.hb.bot;

import com.example.hb.config.TelegramBotConfig;
import com.example.hb.service.FileIdCacheService;
import com.example.hb.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class EvgenBot extends TelegramLongPollingBot {

    private final TelegramBotConfig config;
    private final VideoService videoService;
    private final FileIdCacheService fileIdCacheService;

    @Autowired
    public EvgenBot(TelegramBotConfig config, VideoService videoService, FileIdCacheService fileIdCacheService) {
        super(config.getBotToken());
        this.config = config;
        this.videoService = videoService;
        this.fileIdCacheService = fileIdCacheService;
    }

    @Override
    public String getBotUsername() {
        return config.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return config.getBotToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String firstName = message.getFrom().getFirstName();
            String lastName = message.getFrom().getLastName();
            String fullName = (firstName != null ? firstName : "") + 
                             (lastName != null ? " " + lastName : "").trim();
            if (fullName.isEmpty()) {
                fullName = firstName != null ? firstName : "Пользователь";
            }

            try {
                // Обработка видео для получения File ID
                if (message.hasVideo()) {
                    handleVideoReceived(chatId, message);
                    return;
                }

                // Обработка текстовых сообщений
                if (message.hasText()) {
                    String text = message.getText();
                    if (text.equals("/start")) {
                        handleStart(chatId, fullName);
                    } else if (text.equals("😛 Анекдот")) {
                        handleJoke(chatId);
                    } else if (text.equals("✅ Меню")) {
                        handleMenu(chatId);
                    } else if (text.equals("🎉 Поздравление")) {
                        handleCongratulations(chatId);
                    } else if (text.equals("🎲 Создание монополии")) {
                        handleMonopoly(chatId);
                    }
                }
            } catch (TelegramApiException e) {
                log.error("Error processing message: {}", e.getMessage(), e);
            }
        }
    }

    private void handleVideoReceived(Long chatId, Message message) throws TelegramApiException {
        String fileId = message.getVideo().getFileId();
        String fileName = message.getVideo().getFileName();
        
        log.info("Received video. File ID: {}, File Name: {}", fileId, fileName);
        
        SendMessage reply = new SendMessage();
        reply.setChatId(chatId.toString());
        reply.setText("✅ Видео получено!\n\n" +
                "File ID: `" + fileId + "`\n\n" +
                "Скопируйте этот File ID и добавьте в application.properties:\n" +
                "`telegram.video.congratulations=" + fileId + "`\n" +
                "или\n" +
                "`telegram.video.monopoly=" + fileId + "`");
        reply.setParseMode("Markdown");
        reply.setReplyMarkup(createKeyboard());
        execute(reply);
    }

    private void handleStart(Long chatId, String fullName) throws TelegramApiException {
        // Логируем chatId для настройки предзагрузки
        log.info("User started bot. Chat ID: {} (use this for telegram.preload.chatId in application.properties)", chatId);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Как я могу тебе помочь?");
        message.setReplyMarkup(createKeyboard());
        execute(message);
    }

    private void handleJoke(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("😛 Анекдот\n\n" +
                "Мы продавали диван. Приехали покупатели, увезли.\n" +
                "Через два часа раздаётся телефонный звонок:\n" +
                "- У вас есть кот?\n" +
                "- Есть.\n" +
                "- Серый? Полосатый?\n" +
                "- Да...\n" +
                "- Он не хочет расставаться с диваном.");
        message.setReplyMarkup(createKeyboard());
        execute(message);
    }

    private void handleMenu(Long chatId) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Меню ещё не настроено, но всё впереди!");
        message.setReplyMarkup(createKeyboard());
        execute(message);
    }

    private void handleCongratulations(Long chatId) throws TelegramApiException {
        String mediaPath = "media/IMG_0128.MOV"; // путь из конфига
        
        // Определяем тип файла по расширению
        boolean isVideo = mediaPath.toLowerCase().endsWith(".mp4") || 
                         mediaPath.toLowerCase().endsWith(".mov") || 
                         mediaPath.toLowerCase().endsWith(".avi") || 
                         mediaPath.toLowerCase().endsWith(".mkv");
        
        // 1. Проверяем File ID из application.properties (приоритет)
        if (videoService.hasCongratulationsVideo()) {
            sendMediaByFileId(chatId, videoService.getCongratulationsVideoFileId(), isVideo);
            return;
        }
        
        // 2. Проверяем кэш File ID (быстрая отправка)
        String cachedFileId = fileIdCacheService.getFileId(mediaPath);
        if (cachedFileId != null && !cachedFileId.isEmpty()) {
            log.info("Using cached File ID for congratulations");
            sendMediaByFileId(chatId, cachedFileId, isVideo);
            return;
        }
        
        // 3. Если File ID нет, загружаем файл и кэшируем (только первый раз)
        if (fileIdCacheService.fileExists(mediaPath)) {
            log.info("Uploading file for congratulations (first time), will cache File ID");
            
            // Отправляем уведомление о загрузке
            SendMessage loadingMessage = new SendMessage();
            loadingMessage.setChatId(chatId.toString());
            loadingMessage.setText("⏳ Загружаю видео... Пожалуйста, подождите.");
            execute(loadingMessage);
            
            try {
                String fileName = videoService.getCongratulationsMediaFileName();
                // Загружаем файл - он автоматически отправится пользователю и вернет File ID
                String fileId = uploadAndCacheMedia(chatId, mediaPath, fileName);
                // НЕ отправляем второй раз - файл уже отправлен при загрузке
                if (fileId != null) {
                    log.info("File uploaded and cached. File ID: {}", fileId);
                }
            } catch (Exception e) {
                throw e;
            }
        } else {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🎉 Поздравление\n\n" +
                    "Медиафайл ещё не настроен.\n\n" +
                    "Для настройки:\n" +
                    "1. Поместите файл (фото или видео) в папку src/main/resources/media/\n" +
                    "2. Или укажите File ID в application.properties как telegram.video.congratulations");
            message.setReplyMarkup(createKeyboard());
            execute(message);
        }
    }

    private void handleMonopoly(Long chatId) throws TelegramApiException {
        String mediaPath = "media/IMG_0451.MP4"; // путь из конфига
        
        // Определяем тип файла по расширению
        boolean isVideo = mediaPath.toLowerCase().endsWith(".mp4") || 
                         mediaPath.toLowerCase().endsWith(".mov") || 
                         mediaPath.toLowerCase().endsWith(".avi") || 
                         mediaPath.toLowerCase().endsWith(".mkv");
        
        // 1. Проверяем File ID из application.properties (приоритет)
        if (videoService.hasMonopolyVideo()) {
            sendMediaByFileId(chatId, videoService.getMonopolyVideoFileId(), isVideo);
            return;
        }
        
        // 2. Проверяем кэш File ID (быстрая отправка)
        String cachedFileId = fileIdCacheService.getFileId(mediaPath);
        if (cachedFileId != null && !cachedFileId.isEmpty()) {
            log.info("Using cached File ID for monopoly");
            sendMediaByFileId(chatId, cachedFileId, isVideo);
            return;
        }
        
        // 3. Если File ID нет, загружаем файл и кэшируем (только первый раз)
        if (fileIdCacheService.fileExists(mediaPath)) {
            log.info("Uploading file for monopoly (first time), will cache File ID");
            
            // Отправляем уведомление о загрузке
            SendMessage loadingMessage = new SendMessage();
            loadingMessage.setChatId(chatId.toString());
            loadingMessage.setText("⏳ Загружаю видео... Пожалуйста, подождите.");
            execute(loadingMessage);
            
            try {
                String fileName = videoService.getMonopolyMediaFileName();
                // Загружаем файл - он автоматически отправится пользователю и вернет File ID
                String fileId = uploadAndCacheMedia(chatId, mediaPath, fileName);
                // НЕ отправляем второй раз - файл уже отправлен при загрузке
                if (fileId != null) {
                    log.info("File uploaded and cached. File ID: {}", fileId);
                }
            } catch (Exception e) {
                throw e;
            }
        } else {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("🎲 Создание монополии\n\n" +
                    "Медиафайл ещё не настроен.\n\n" +
                    "Для настройки:\n" +
                    "1. Поместите файл (фото или видео) в папку src/main/resources/media/\n" +
                    "2. Или укажите File ID в application.properties как telegram.video.monopoly");
            message.setReplyMarkup(createKeyboard());
            execute(message);
        }
    }

    /**
     * Быстрая отправка медиафайла по File ID (без загрузки)
     */
    private void sendMediaByFileId(Long chatId, String fileId, boolean isVideo) throws TelegramApiException {
        if (isVideo) {
            SendVideo video = new SendVideo();
            video.setChatId(chatId.toString());
            video.setVideo(new InputFile(fileId));
            video.setReplyMarkup(createKeyboard());
            execute(video);
        } else {
            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId.toString());
            photo.setPhoto(new InputFile(fileId));
            photo.setReplyMarkup(createKeyboard());
            execute(photo);
        }
    }

    /**
     * Загружает медиафайл в Telegram и сохраняет File ID в кэш (публичный метод для предзагрузки)
     * @return File ID загруженного файла
     */
    public String uploadAndCacheMediaSync(Long chatId, String mediaPath, String fileName) throws TelegramApiException {
        return uploadAndCacheMedia(chatId, mediaPath, fileName);
    }

    /**
     * Загружает медиафайл в Telegram и сохраняет File ID в кэш
     * @return File ID загруженного файла
     */
    private String uploadAndCacheMedia(Long chatId, String mediaPath, String fileName) throws TelegramApiException {
        try {
            java.io.InputStream fileStream = fileIdCacheService.getResource(mediaPath).getInputStream();
            String lowerFileName = fileName.toLowerCase();
            Message sentMessage;
            
            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || 
                lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif")) {
                // Загружаем фото
                SendPhoto photo = new SendPhoto();
                photo.setChatId(chatId.toString());
                photo.setPhoto(new InputFile(fileStream, fileName));
                sentMessage = execute(photo);
                
                if (sentMessage != null && sentMessage.getPhoto() != null && !sentMessage.getPhoto().isEmpty()) {
                    String fileId = sentMessage.getPhoto().get(sentMessage.getPhoto().size() - 1).getFileId();
                    fileIdCacheService.saveFileId(mediaPath, fileId);
                    return fileId;
                }
            } else if (lowerFileName.endsWith(".mp4") || lowerFileName.endsWith(".mov") || 
                       lowerFileName.endsWith(".avi") || lowerFileName.endsWith(".mkv")) {
                // Загружаем видео
                SendVideo video = new SendVideo();
                video.setChatId(chatId.toString());
                video.setVideo(new InputFile(fileStream, fileName));
                sentMessage = execute(video);
                
                if (sentMessage != null && sentMessage.getVideo() != null) {
                    String fileId = sentMessage.getVideo().getFileId();
                    fileIdCacheService.saveFileId(mediaPath, fileId);
                    return fileId;
                }
            }
        } catch (Exception e) {
            log.error("Error uploading and caching media file: {}", mediaPath, e);
            throw new TelegramApiException("Failed to upload media file", e);
        }
        return null;
    }

    /**
     * Отправляет медиафайл (фото или видео) из локального файла
     * @deprecated Используйте sendMediaByFileId для быстрой отправки
     */
    @Deprecated
    private void sendMediaFromFile(Long chatId, java.io.InputStream fileStream, String fileName) 
            throws TelegramApiException {
        if (fileStream == null) {
            log.warn("File stream is null for file: {}", fileName);
            return;
        }

        try {
            // Определяем тип файла по расширению
            String lowerFileName = fileName.toLowerCase();
            
            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") || 
                lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif")) {
                // Отправляем фото
                SendPhoto photo = new SendPhoto();
                photo.setChatId(chatId.toString());
                photo.setPhoto(new InputFile(fileStream, fileName));
                photo.setReplyMarkup(createKeyboard());
                execute(photo);
            } else if (lowerFileName.endsWith(".mp4") || lowerFileName.endsWith(".mov") || 
                       lowerFileName.endsWith(".avi") || lowerFileName.endsWith(".mkv")) {
                // Отправляем видео
                SendVideo video = new SendVideo();
                video.setChatId(chatId.toString());
                video.setVideo(new InputFile(fileStream, fileName));
                video.setReplyMarkup(createKeyboard());
                execute(video);
            } else {
                log.warn("Unsupported file type: {}", fileName);
                SendMessage message = new SendMessage();
                message.setChatId(chatId.toString());
                message.setText("❌ Неподдерживаемый тип файла: " + fileName);
                message.setReplyMarkup(createKeyboard());
                execute(message);
            }
        } catch (Exception e) {
            log.error("Error sending media file: {}", fileName, e);
            throw new TelegramApiException("Failed to send media file", e);
        }
    }

    private ReplyKeyboardMarkup createKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Только две кнопки: Поздравление и Создание монополии
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🎉 Поздравление"));
        row1.add(new KeyboardButton("🎲 Создание монополии"));
        keyboard.add(row1);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }
}
