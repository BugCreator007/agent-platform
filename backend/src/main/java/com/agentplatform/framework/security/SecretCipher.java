package com.agentplatform.framework.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感密钥加解密器（AES-256-GCM）。
 *
 * <p>位于技术框架的安全层，用于加密模型厂商 API Key 等需要可逆保存的敏感配置；
 * 加密后的密文（IV + 密文 + 认证标签，Base64 编码）存储于数据库。
 * 注意：用户密码不使用该工具，密码只允许 BCrypt 单向哈希。</p>
 *
 * <p>安全约束：密钥派生自配置项 {@code app.security.secrets-key}（SHA-256 后取 32 字节），
 * 生产环境必须通过环境变量注入并妥善保管；更换密钥后旧密文将无法解密。</p>
 */
@Component
public class SecretCipher {
    /** GCM 认证标签长度（位） */
    private static final int GCM_TAG_BITS = 128;
    /** GCM 推荐 IV 长度（字节），每次加密随机生成 */
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * 初始化加密器：对配置密钥做 SHA-256 摘要，得到固定 32 字节 AES-256 密钥。
     *
     * @param secret 配置中的主密钥字符串
     * @throws IllegalStateException 初始化失败时抛出
     */
    public SecretCipher(@Value("${app.security.secrets-key:agent-platform-local-secrets-change-me}") String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化密钥加密器", e);
        }
    }

    /**
     * 加密明文。
     *
     * <p>每次生成随机 IV，与密文拼接后整体 Base64 输出，格式为 {@code Base64(IV || ciphertext)}。</p>
     *
     * @param plaintext 明文，空值返回 {@code null}
     * @return Base64 密文；空入参返回 {@code null}
     * @throws IllegalStateException 加密失败时抛出
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
            // 随机 IV 防止相同明文产生相同密文，IV 无需保密但必须随密文保存
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("密钥加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * <p>解密失败（密钥轮换、密文被篡改、格式错误）时返回 {@code null} 而非抛异常，
     * 由调用方降级处理（例如视为未配置），避免因历史脏数据阻断页面加载。</p>
     *
     * @param ciphertext Base64 密文
     * @return 明文；空入参或解密失败返回 {@code null}
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            // GCM 在 doFinal 时校验认证标签，密文被篡改会抛出 AEADBadTagException
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
