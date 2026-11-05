package com.eladminplus.storage.engine;

/** 存储引擎异常基类：实现把各自 IO 差异归一为对象缺失与引擎不可用两语义，服务层翻译为业务响应。 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
