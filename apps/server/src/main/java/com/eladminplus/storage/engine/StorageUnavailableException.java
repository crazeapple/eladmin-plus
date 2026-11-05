package com.eladminplus.storage.engine;

/** 存储引擎不可用：不可达 / 凭据错误 / 读写失败，服务层转 5xx 含「存储服务不可用」明确消息。 */
public class StorageUnavailableException extends StorageException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
