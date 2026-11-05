package com.eladminplus.storage.engine;

import com.eladminplus.storage.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/** RustFS（S3 兼容）引擎：MinIO Java client 对接，桶不存在自动创建（幂等懒建）。 */
@Component
public class RustfsStorage implements StoragePort {

    private final MinioClient client;
    private final String bucket;

    public RustfsStorage(StorageProperties properties) {
        StorageProperties.Rustfs config = properties.rustfs();
        this.bucket = config.bucket();
        this.client = MinioClient.builder()
                .endpoint(config.endpoint())
                .credentials(config.accessKey(), config.secretKey())
                .build();
    }

    @Override
    public String id() {
        return "rustfs";
    }

    @Override
    public void store(String key, InputStream in, long size, String contentType) {
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(in, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw translate("RustFS 写入失败：" + key, e);
        }
    }

    @Override
    public InputStream fetch(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw translate("RustFS 读取失败：" + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            // S3 删除对不存在的键幂等成功，与端口契约同形
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw translate("RustFS 删除失败：" + key, e);
        }
    }

    /** 桶幂等确认：每次写前确认，不存在即建（桶可被外部落删，删后写自动重建）；不可达即抛（服务层转 5xx） */
    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new StorageUnavailableException("RustFS 桶确认失败：" + bucket, e);
        }
    }

    /** MinIO client 异常族广，统一归一：对象缺失转 404 语义，其余按引擎不可用 */
    private static StorageException translate(String what, Exception e) {
        if (e instanceof ErrorResponseException response
                && "NoSuchKey".equals(response.errorResponse().code())) {
            return new StorageObjectMissingException(what, e);
        }
        return new StorageUnavailableException(what, e);
    }
}
