package com.eladminplus.ops.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.domain.DatabaseConfig;
import com.eladminplus.ops.dto.DatabaseConfigSaveRequest;
import com.eladminplus.ops.dto.DatabaseConfigView;
import com.eladminplus.ops.mapper.DatabaseConfigMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 连接配置服务：增改查删；密码明文存库、响应一律经视图脱敏（不回显），更新留空保持原值。
 * 删除连接配置不动备份记录（历史事实不受配置删除影响）。
 */
@Service
public class DatabaseConfigService {

    /** 列容量截断校验上限：host / 库名 / 账号 / 密码 / 备注 255（VARCHAR） */
    private static final int TEXT_MAX = 255;

    private final DatabaseConfigMapper databaseConfigMapper;

    public DatabaseConfigService(DatabaseConfigMapper databaseConfigMapper) {
        this.databaseConfigMapper = databaseConfigMapper;
    }

    /** 新增：必填项与端口范围校验在先，密码新增时必填 */
    public Long create(DatabaseConfigSaveRequest request) {
        validate(request);
        if (!StringUtils.hasText(request.password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.ops.password-required");
        }
        DatabaseConfig row = new DatabaseConfig();
        apply(row, request);
        row.setPassword(request.password());
        databaseConfigMapper.insert(row);
        return row.getId();
    }

    /** 更新：密码留空保持原值，其余字段全量覆盖 */
    public void update(Long id, DatabaseConfigSaveRequest request) {
        DatabaseConfig row = get(id);
        validate(request);
        apply(row, request);
        if (StringUtils.hasText(request.password())) {
            row.setPassword(request.password());
        }
        databaseConfigMapper.updateById(row);
    }

    /** 行读取：不存在 id 按语义拒 404（详情、更新、删除与备份共用入口） */
    public DatabaseConfig get(Long id) {
        DatabaseConfig row = databaseConfigMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        return row;
    }

    /** 全量列表：配置数量小（每库一条），不分页；新配置在前 */
    public List<DatabaseConfigView> list() {
        return databaseConfigMapper
                .selectList(Wrappers.<DatabaseConfig>lambdaQuery().orderByDesc(DatabaseConfig::getId))
                .stream()
                .map(DatabaseConfigView::from)
                .toList();
    }

    /** 删除：仅删配置行，关联备份记录保留 */
    public void remove(Long id) {
        get(id);
        databaseConfigMapper.deleteById(id);
    }

    /** 必填与形态校验：文本超列容、端口越界、必填缺失一律 400 */
    private static void validate(DatabaseConfigSaveRequest request) {
        if (!StringUtils.hasText(request.host()) || !StringUtils.hasText(request.name()) || !StringUtils.hasText(request.userName())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65535) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
        }
        if (StringUtils.hasText(request.password()) && request.password().length() > TEXT_MAX) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
        }
        if (exceeds(request.host()) || exceeds(request.name()) || exceeds(request.userName()) || exceeds(request.remark())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
        }
    }

    private static boolean exceeds(String value) {
        return value != null && value.length() > TEXT_MAX;
    }

    private static void apply(DatabaseConfig row, DatabaseConfigSaveRequest request) {
        row.setHost(request.host());
        row.setPort(request.port());
        row.setName(request.name());
        row.setUserName(request.userName());
        row.setRemark(request.remark());
    }
}
