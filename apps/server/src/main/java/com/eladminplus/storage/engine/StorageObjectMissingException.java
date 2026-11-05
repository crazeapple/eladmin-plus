package com.eladminplus.storage.engine;

/** 存储对象缺失：记录行在册而对象已不在，服务层转 404。 */
public class StorageObjectMissingException extends StorageException {

    public StorageObjectMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
