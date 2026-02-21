package com.example.hb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Slf4j
@Service
public class FileIdCacheService {

    private final ResourceLoader resourceLoader;
    private final Properties cache;
    private final Path cacheFilePath;

    public FileIdCacheService(ResourceLoader resourceLoader,
                             @Value("${telegram.cache.file:file-id-cache.properties}") String cacheFileName) {
        this.resourceLoader = resourceLoader;
        this.cache = new Properties();
        this.cacheFilePath = Paths.get(cacheFileName != null ? cacheFileName : "file-id-cache.properties");
        loadCache();
    }

    /**
     * Получить File ID из кэша для указанного файла
     */
    public String getFileId(String filePath) {
        return cache.getProperty(filePath);
    }

    /**
     * Сохранить File ID в кэш
     */
    public void saveFileId(String filePath, String fileId) {
        cache.setProperty(filePath, fileId);
        saveCache();
        log.info("Saved File ID for {}: {}", filePath, fileId);
    }

    /**
     * Проверить, есть ли File ID в кэше
     */
    public boolean hasFileId(String filePath) {
        return cache.containsKey(filePath) && cache.getProperty(filePath) != null && !cache.getProperty(filePath).isEmpty();
    }

    /**
     * Получить полный путь к файлу из resources
     */
    public Resource getResource(String path) {
        return resourceLoader.getResource("classpath:" + path);
    }

    /**
     * Проверить, существует ли файл
     */
    public boolean fileExists(String path) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + path);
            return resource.exists() && resource.isReadable();
        } catch (Exception e) {
            return false;
        }
    }

    private void loadCache() {
        try {
            if (Files.exists(cacheFilePath)) {
                try (FileInputStream fis = new FileInputStream(cacheFilePath.toFile())) {
                    cache.load(fis);
                    log.info("Loaded {} file IDs from cache", cache.size());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load cache file: {}", e.getMessage());
        }
    }

    private void saveCache() {
        try {
            // Создаём директорию только если parent не null
            Path parent = cacheFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileOutputStream fos = new FileOutputStream(cacheFilePath.toFile())) {
                cache.store(fos, "Telegram File ID Cache");
            }
        } catch (IOException e) {
            log.error("Failed to save cache file: {}", e.getMessage(), e);
        }
    }
}
