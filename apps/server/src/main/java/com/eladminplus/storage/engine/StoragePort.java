package com.eladminplus.storage.engine;

import java.io.InputStream;

/** 存储引擎端口：上传 / 下载 / 删除的引擎无关入口，实现按 id 注册，消费按 storage_file 行内引擎字段路由。 */
public interface StoragePort {

    /** 引擎标识：storage_file.engine 落库值，亦作路由键 */
    String id();

    /** 写对象：键由服务层生成（{yyyyMM}/{uuid}.{ext}），contentType 落对象元数据 */
    void store(String key, InputStream in, long size, String contentType);

    /** 开对象读流：调用方读完负责关闭 */
    InputStream fetch(String key);

    /** 删对象：对象本就不存在视为已删（幂等） */
    void delete(String key);
}
