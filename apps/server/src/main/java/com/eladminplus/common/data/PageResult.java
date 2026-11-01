package com.eladminplus.common.data;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 * 分页结果：page 从 0 起，从 MyBatis-Plus 记录回读时换算集中在此。
 */
public record PageResult<T>(List<T> content, long total, int page, int size) {

    public static <T> PageResult<T> from(Page<T> mpPage) {
        return new PageResult<>(mpPage.getRecords(), mpPage.getTotal(),
                (int) (mpPage.getCurrent() - 1), (int) mpPage.getSize());
    }
}
