package com.eladminplus.auth.rsa;

import com.eladminplus.common.R;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录公钥下发：keyId 与公钥同发，密文回传时携带 keyId 定位解密密钥。 */
@RestController
@RequestMapping("/auth")
public class PublicKeyController {

    private final RsaKeyService rsaKeyService;

    public PublicKeyController(RsaKeyService rsaKeyService) {
        this.rsaKeyService = rsaKeyService;
    }

    @GetMapping("/public-key")
    public R<Map<String, String>> publicKey() {
        return R.ok(rsaKeyService.publicKeyPayload());
    }
}
