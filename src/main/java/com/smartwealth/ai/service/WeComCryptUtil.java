package com.smartwealth.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

/**
 * Enterprise WeChat (WeCom) message encryption/decryption.
 * Algorithm: AES-256-CBC with PKCS7 padding + SHA1 signature.
 */
public final class WeComCryptUtil {

    private static final Logger log = LoggerFactory.getLogger(WeComCryptUtil.class);

    private final byte[] aesKey;
    private final String token;
    private final String corpId;

    public WeComCryptUtil(String token, String encodingAesKey, String corpId) {
        this.token = token;
        this.corpId = corpId;
        this.aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
    }

    /**
     * Verify signature from WeCom server.
     */
    public boolean verifySignature(String signature, String timestamp, String nonce, String echoStr) {
        return signature.equals(sha1(token, timestamp, nonce, echoStr));
    }

    /**
     * Decrypt the encrypted XML message body.
     */
    public String decrypt(String encryptedXml) {
        try {
            String encrypt = extractCdata(encryptedXml, "Encrypt");
            if (encrypt == null) {
                log.warn("No <Encrypt> found in XML");
                return null;
            }
            byte[] cipherText = Base64.getDecoder().decode(encrypt);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            byte[] decrypted = cipher.doFinal(cipherText);

            // Remove PKCS7 padding
            int pad = decrypted[decrypted.length - 1] & 0xFF;
            byte[] unpadded = Arrays.copyOf(decrypted, decrypted.length - pad);

            // Layout: 16 random bytes + 4 bytes msg length (big-endian) + msg + corpId
            byte[] msgBytes = Arrays.copyOfRange(unpadded, 20, unpadded.length);
            return new String(msgBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt WeCom message", e);
            return null;
        }
    }

    /**
     * Encrypt and wrap reply message for WeCom.
     */
    public String encryptReply(String plainXml, String timestamp, String nonce) {
        try {
            // Add random prefix
            byte[] random = new byte[16];
            for (int i = 0; i < 16; i++) random[i] = (byte) (Math.random() * 256);
            byte[] plainBytes = plainXml.getBytes(StandardCharsets.UTF_8);
            byte[] corpIdBytes = corpId.getBytes(StandardCharsets.UTF_8);
            byte[] msgLen = new byte[] {
                    (byte) (plainBytes.length >> 24),
                    (byte) (plainBytes.length >> 16),
                    (byte) (plainBytes.length >> 8),
                    (byte) plainBytes.length
            };

            // Assemble: random + msgLen + msg + corpId
            byte[] assembled = new byte[16 + 4 + plainBytes.length + corpIdBytes.length];
            System.arraycopy(random, 0, assembled, 0, 16);
            System.arraycopy(msgLen, 0, assembled, 16, 4);
            System.arraycopy(plainBytes, 0, assembled, 20, plainBytes.length);
            System.arraycopy(corpIdBytes, 0, assembled, 20 + plainBytes.length, corpIdBytes.length);

            // PKCS7 padding
            int blockSize = 32;
            int padLen = blockSize - (assembled.length % blockSize);
            byte[] padded = new byte[assembled.length + padLen];
            System.arraycopy(assembled, 0, padded, 0, assembled.length);
            for (int i = assembled.length; i < padded.length; i++) padded[i] = (byte) padLen;

            // AES encrypt
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(aesKey, 0, 16);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
            byte[] encrypted = cipher.doFinal(padded);
            String encrypt = Base64.getEncoder().encodeToString(encrypted);

            String signature = sha1(token, timestamp, nonce, encrypt);

            return """
                    <xml>
                    <Encrypt><![CDATA[%s]]></Encrypt>
                    <MsgSignature><![CDATA[%s]]></MsgSignature>
                    <TimeStamp>%s</TimeStamp>
                    <Nonce><![CDATA[%s]]></Nonce>
                    </xml>""".formatted(encrypt, signature, timestamp, nonce);

        } catch (Exception e) {
            log.error("Failed to encrypt WeCom reply", e);
            return null;
        }
    }

    private static String sha1(String... values) {
        try {
            Arrays.sort(values);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            for (String v : values) {
                md.update(v.getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    static String extractCdata(String xml, String tag) {
        String open = "<" + tag + "><![CDATA[";
        String close = "]]></" + tag + ">";
        int start = xml.indexOf(open);
        if (start < 0) return null;
        start += open.length();
        int end = xml.indexOf(close, start);
        if (end < 0) return null;
        return xml.substring(start, end);
    }
}
