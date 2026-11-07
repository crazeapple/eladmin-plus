package com.eladminplus.ops.service;

import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.OpsBackupProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 备份执行器：HTTP 调 compose 内网备份 sidecar（POST /cgi-bin/dump?db={库名}，凭据经请求头传入
 * 不进 URL），sidecar 以自带 dump 客户端直连 mysql 容器，gzip 流式响应直写目标文件
 * （应用容器与 sidecar 内均不落盘明文）。sidecar 不可达、转储出错都归「备份执行失败」5xx 含原因。
 * 读超时按大库转储余量给足（10 分钟），连不通 5 秒即失败。
 */
@Component
public class MysqlDumpExecutor {

    /** 失败报错携带的原因尾段长度上限（报错可读且不无限长） */
    private static final int REASON_TAIL_MAX = 500;

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int READ_TIMEOUT_MILLIS = 600_000;

    private final RestClient restClient;

    public MysqlDumpExecutor(OpsBackupProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(READ_TIMEOUT_MILLIS);
        this.restClient = RestClient.builder().requestFactory(factory).baseUrl(properties.sidecarUrl()).build();
    }

    /** 执行导出：2xx 的 gzip 流直写 target；非 2xx 与不可达按执行失败 5xx 拒绝（原因尾段随响应） */
    public void dump(String database, String userName, String password, Path target) {
        try {
            restClient
                    .post()
                    .uri(uri -> uri.path("/cgi-bin/dump").queryParam("db", database).build())
                    .header("X-Dump-User", userName)
                    .header("X-Dump-Pass", password)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            try (InputStream in = response.getBody()) {
                                in.transferTo(Files.newOutputStream(target));
                            }
                            return null;
                        }
                        String reason =
                                new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                        throw new BusinessException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "备份执行失败：备份端点状态 " + response.getStatusCode().value() + "（" + tail(reason)
                                        + "）");
                    });
        } catch (ResourceAccessException e) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份端点不可达（" + e.getMessage() + "）");
        }
    }

    private static String tail(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= REASON_TAIL_MAX ? trimmed : trimmed.substring(trimmed.length() - REASON_TAIL_MAX);
    }
}
