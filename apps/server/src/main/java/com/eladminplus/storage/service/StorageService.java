package com.eladminplus.storage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.storage.StorageProperties;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.dto.StorageFilePageQuery;
import com.eladminplus.storage.dto.StorageFileView;
import com.eladminplus.storage.engine.StorageObjectMissingException;
import com.eladminplus.storage.engine.StoragePort;
import com.eladminplus.storage.engine.StorageUnavailableException;
import com.eladminplus.storage.mapper.StorageFileMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 存储域服务：上传落对象与行、下载与删除按行内引擎字段路由（混引擎历史可读）、删除先对象后行。 */
@Service
public class StorageService {

    /** 列容量截断上限：原名 255（VARCHAR），扩展名 32 */
    private static final int NAME_MAX = 255;
    private static final int EXT_MAX = 32;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final StorageFileMapper storageFileMapper;
    private final StorageProperties properties;
    private final Map<String, StoragePort> ports;

    public StorageService(
            StorageFileMapper storageFileMapper, StorageProperties properties, List<StoragePort> ports) {
        this.storageFileMapper = storageFileMapper;
        this.properties = properties;
        this.ports = ports.stream().collect(Collectors.toUnmodifiableMap(StoragePort::id, Function.identity()));
    }

    /** 上传：体积超限拒 413；键形 {yyyyMM}/{uuid}.{ext}；先落对象后落行（对象写失败不产生孤儿行） */
    public StorageFile upload(MultipartFile file, String uploader) {
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "error.storage.too-large");
        }
        String ext = extOf(file.getOriginalFilename());
        String key = LocalDate.now().format(MONTH_FORMAT) + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String mime = file.getContentType();
        try (InputStream in = file.getInputStream()) {
            portOf(properties.engine()).store(key, in, file.getSize(), mime);
        } catch (StorageUnavailableException | IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "error.storage.unavailable");
        }

        StorageFile row = new StorageFile();
        row.setName(truncate(file.getOriginalFilename(), NAME_MAX));
        row.setStorageKey(key);
        row.setExt(ext.isEmpty() ? null : ext);
        row.setSize(file.getSize());
        row.setMime(mime);
        row.setEngine(properties.engine());
        row.setUploader(uploader);
        storageFileMapper.insert(row);
        return row;
    }

    /** 行读取：不存在 id 按语义拒 404（下载与删除共用入口） */
    public StorageFile get(Long id) {
        StorageFile row = storageFileMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        return row;
    }

    /** 开下载流：按行内引擎字段路由（不受当前配置引擎影响），对象缺失同样按 404 语义 */
    public InputStream open(StorageFile row) {
        try {
            return portOf(row.getEngine()).fetch(row.getStorageKey());
        } catch (StorageObjectMissingException e) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        } catch (StorageUnavailableException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "error.storage.unavailable");
        }
    }

    /** 删除：先删存储对象后删记录行——对象删除失败行保留（防孤儿记录），行删除成功对象必已删 */
    public void remove(Long id) {
        StorageFile row = get(id);
        try {
            portOf(row.getEngine()).delete(row.getStorageKey());
        } catch (StorageUnavailableException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "error.storage.unavailable");
        }
        storageFileMapper.deleteById(id);
    }

    /** 存储文件分页：原名模糊、上传人精确、创建时间范围筛选，新记录在前 */
    public PageResult<StorageFileView> page(StorageFilePageQuery query) {
        var wrapper = Wrappers.<StorageFile>lambdaQuery();
        if (StringUtils.hasText(query.name())) {
            wrapper.like(StorageFile::getName, query.name());
        }
        if (StringUtils.hasText(query.uploader())) {
            wrapper.eq(StorageFile::getUploader, query.uploader());
        }
        if (query.startAt() != null) {
            wrapper.ge(StorageFile::getCreatedAt, query.startAt());
        }
        if (query.endAt() != null) {
            wrapper.le(StorageFile::getCreatedAt, query.endAt());
        }
        wrapper.orderByDesc(StorageFile::getId);
        Page<StorageFile> result =
                storageFileMapper.selectPage(new PageRequest(pageOf(query), sizeOf(query)).toMpPage(), wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(StorageFileView::from).toList(),
                result.getTotal(),
                (int) (result.getCurrent() - 1),
                (int) result.getSize());
    }

    private static int pageOf(StorageFilePageQuery query) {
        return query.page() == null ? 0 : query.page();
    }

    private static int sizeOf(StorageFilePageQuery query) {
        return query.size() == null ? 10 : query.size();
    }

    private StoragePort portOf(String engine) {
        StoragePort port = ports.get(engine);
        if (port == null) {
            // 行内引擎值超出在册引擎：数据异常按不可用拒（不静默吞）
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "error.storage.unavailable");
        }
        return port;
    }

    /** 扩展名归一：小写无点；无名 / 无后缀 / 超列容的长尾按无扩展名处理 */
    private static String extOf(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        String ext = originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.length() > EXT_MAX ? "" : ext;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
