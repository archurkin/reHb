package com.example.hb.config;

import com.example.hb.bot.EvgenBot;
import com.example.hb.service.FileIdCacheService;
import com.example.hb.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Component
public class BotRegistrationRunner implements ApplicationRunner {

    private final EvgenBot evgenBot;
    private final VideoService videoService;
    private final FileIdCacheService fileIdCacheService;
    private final Executor mediaPreloadExecutor;

    @Value("${telegram.preload.chatId:}")
    private String preloadChatId;

    @Value("${telegram.preload.enabled:true}")
    private boolean preloadEnabled;

    @Value("${telegram.preload.delay:1000}")
    private long preloadDelay;

    public BotRegistrationRunner(EvgenBot evgenBot, VideoService videoService, 
                                FileIdCacheService fileIdCacheService,
                                @Qualifier("mediaPreloadExecutor") Executor mediaPreloadExecutor) {
        this.evgenBot = evgenBot;
        this.videoService = videoService;
        this.fileIdCacheService = fileIdCacheService;
        this.mediaPreloadExecutor = mediaPreloadExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(evgenBot);
            log.info("Telegram bot successfully registered");
            if (preloadEnabled) {
                preloadMediaFiles();
            }
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram bot (app will keep running, set TELEGRAM_BOT_TOKEN if deploying): {}", e.getMessage());
            // Не падаем — чтобы веб-сервер поднялся и healthcheck прошёл
        }
    }

    /**
     * Предзагрузка медиафайлов в фоновом режиме для получения File ID
     * Использует параллельную загрузку для ускорения процесса
     */
    private void preloadMediaFiles() {
        CompletableFuture.runAsync(() -> {
            try {
                // Небольшая задержка, чтобы бот точно был готов
                Thread.sleep(preloadDelay);
                
                String chatId = preloadChatId;
                if (chatId == null || chatId.isEmpty()) {
                    log.warn("telegram.preload.chatId not set, skipping preload. " +
                            "Set it in application.properties to enable preloading.");
                    return;
                }

                log.info("Starting parallel media files preload...");
                long startTime = System.currentTimeMillis();
                
                Long chatIdLong = Long.parseLong(chatId);
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                // Параллельная предзагрузка файла поздравления
                String congratulationsPath = "media/IMG_0128.MOV";
                if (fileIdCacheService.fileExists(congratulationsPath) && 
                    !fileIdCacheService.hasFileId(congratulationsPath)) {
                    CompletableFuture<Void> congratulationsFuture = CompletableFuture.runAsync(() -> {
                        try {
                            log.info("Preloading congratulations file (parallel)...");
                            String fileName = videoService.getCongratulationsMediaFileName();
                            String fileId = evgenBot.uploadAndCacheMediaSync(chatIdLong, congratulationsPath, fileName);
                            if (fileId != null) {
                                log.info("✅ Congratulations file preloaded, File ID: {}", fileId);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to preload congratulations file: {}", e.getMessage());
                        }
                    }, mediaPreloadExecutor);
                    futures.add(congratulationsFuture);
                }

                // Параллельная предзагрузка файла монополии
                String monopolyPath = "media/IMG_0451.MP4";
                if (fileIdCacheService.fileExists(monopolyPath) && 
                    !fileIdCacheService.hasFileId(monopolyPath)) {
                    CompletableFuture<Void> monopolyFuture = CompletableFuture.runAsync(() -> {
                        try {
                            log.info("Preloading monopoly file (parallel)...");
                            String fileName = videoService.getMonopolyMediaFileName();
                            String fileId = evgenBot.uploadAndCacheMediaSync(chatIdLong, monopolyPath, fileName);
                            if (fileId != null) {
                                log.info("✅ Monopoly file preloaded, File ID: {}", fileId);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to preload monopoly file: {}", e.getMessage());
                        }
                    }, mediaPreloadExecutor);
                    futures.add(monopolyFuture);
                }

                // Ждем завершения всех загрузок
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                long endTime = System.currentTimeMillis();
                log.info("✅ Media files preload completed in {} ms (parallel loading)", (endTime - startTime));
            } catch (Exception e) {
                log.error("Error during media files preload", e);
            }
        });
    }
}
