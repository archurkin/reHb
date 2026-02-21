package com.example.hb.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;

@Service
public class VideoService {

    private final ResourceLoader resourceLoader;

    @Value("${telegram.video.congratulations:}")
    private String congratulationsVideoFileId;

    @Value("${telegram.video.monopoly:}")
    private String monopolyVideoFileId;

    // Пути к локальным файлам в resources
    @Value("${telegram.media.congratulations.path:media/congratulations.mp4}")
    private String congratulationsMediaPath;

    @Value("${telegram.media.monopoly.path:media/monopoly.mp4}")
    private String monopolyMediaPath;

    public VideoService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String getCongratulationsVideoFileId() {
        return congratulationsVideoFileId;
    }

    public String getMonopolyVideoFileId() {
        return monopolyVideoFileId;
    }

    public boolean hasCongratulationsVideo() {
        return congratulationsVideoFileId != null && !congratulationsVideoFileId.isEmpty();
    }

    public boolean hasMonopolyVideo() {
        return monopolyVideoFileId != null && !monopolyVideoFileId.isEmpty();
    }

    /**
     * Получить локальный файл для поздравления
     * @return InputStream файла или null, если файл не найден
     */
    public InputStream getCongratulationsMediaFile() {
        return getMediaFile(congratulationsMediaPath);
    }

    /**
     * Получить локальный файл для монополии
     * @return InputStream файла или null, если файл не найден
     */
    public InputStream getMonopolyMediaFile() {
        return getMediaFile(monopolyMediaPath);
    }

    /**
     * Проверить, существует ли локальный файл для поздравления
     */
    public boolean hasCongratulationsMediaFile() {
        return getMediaFile(congratulationsMediaPath) != null;
    }

    /**
     * Проверить, существует ли локальный файл для монополии
     */
    public boolean hasMonopolyMediaFile() {
        return getMediaFile(monopolyMediaPath) != null;
    }

    /**
     * Получить имя файла для поздравления (для определения типа: фото или видео)
     */
    public String getCongratulationsMediaFileName() {
        return new File(congratulationsMediaPath).getName();
    }

    /**
     * Получить имя файла для монополии (для определения типа: фото или видео)
     */
    public String getMonopolyMediaFileName() {
        return new File(monopolyMediaPath).getName();
    }

    private InputStream getMediaFile(String path) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + path);
            if (resource.exists() && resource.isReadable()) {
                return resource.getInputStream();
            }
        } catch (Exception e) {
            // Файл не найден или ошибка чтения
        }
        return null;
    }
}
