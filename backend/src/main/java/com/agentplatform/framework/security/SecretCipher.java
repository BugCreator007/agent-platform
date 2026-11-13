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

@Component
public class SecretCipher {
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.security.secrets-key:agent-platform-local-secrets-change-me}") String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化密钥加密器", e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return null;
        try {
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

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
