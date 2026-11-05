package com.eladminplus.storage.engine;

import com.eladminplus.storage.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;

/** 本地磁盘引擎：目录配置化（storage.local.base-path）；类名避与浏览器 localStorage 同名。 */
@Component
public class DiskStorage implements StoragePort {

    private final Path basePath;

    public DiskStorage(StorageProperties properties) {
        this.basePath = Path.of(properties.local().basePath()).toAbsolutePath().normalize();
    }

    @Override
    public String id() {
        return "local";
    }

    @Override
    public void store(String key, InputStream in, long size, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageUnavailableException("本地磁盘写入失败：" + key, e);
        }
    }

    @Override
    public InputStream fetch(String key) {
        Path target = resolve(key);
        try {
            return Files.newInputStream(target);
        } catch (NoSuchFileException e) {
            throw new StorageObjectMissingException("本地对象不存在：" + key, e);
        } catch (IOException e) {
            throw new StorageUnavailableException("本地磁盘读取失败：" + key, e);
        }
    }

    @Override
    public void delete(String key) {
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageUnavailableException("本地磁盘删除失败：" + key, e);
        }
    }

    /** 键到磁盘路径映射：键为服务层生成的 {yyyyMM}/{uuid}.{ext} 形态，归一后越界即拒（防目录穿越） */
    private Path resolve(String key) {
        Path target = basePath.resolve(key).normalize();
        if (!target.startsWith(basePath)) {
            throw new IllegalStateException("存储键越界：" + key);
        }
        return target;
    }
}
