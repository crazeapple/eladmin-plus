package com.eladminplus.storage.controller;

import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.dto.StorageFilePageQuery;
import com.eladminplus.storage.dto.StorageFileView;
import com.eladminplus.storage.service.StorageService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 存储文件端点：上传 / 分页列表 / 流式下载 / 删除。
 * 权限点 storage:list / storage:upload / storage:del（ROLE_ROOT 直通）；下载不设权限点，登录即可（头像展示同通路）。
 */
@RestController
@RequestMapping("/storage/files")
public class StorageController {

    private final StorageService storageService;

    public StorageController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('storage:upload') || hasAuthority('ROLE_ROOT')")
    public R<StorageFileView> upload(@RequestParam("file") MultipartFile file, Authentication authentication) {
        TokenService.SessionContext context = (TokenService.SessionContext) authentication.getDetails();
        StorageFile row = storageService.upload(file, context.username());
        return R.ok(StorageFileView.from(row));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('storage:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<StorageFileView>> page(StorageFilePageQuery query) {
        return R.ok(storageService.page(query));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        StorageFile row = storageService.get(id);
        InputStream in = storageService.open(row);
        String mime = row.getMime() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : row.getMime();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(row.getName()))
                .contentType(MediaType.parseMediaType(mime))
                .body(new InputStreamResource(in));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('storage:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        storageService.remove(id);
        return R.ok();
    }

    /** 下载响应头：原名经 RFC 5987 编码（非 ASCII 名不乱码），形态 attachment; filename=… */
    private static String contentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
