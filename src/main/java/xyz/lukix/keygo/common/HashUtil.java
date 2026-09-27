package xyz.lukix.keygo.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 摘要工具：激活码只以 SHA-256 哈希形态落库 */
public final class HashUtil {

    private HashUtil() {
    }

    /** 返回 64 位小写十六进制的 SHA-256 摘要 */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制支持的算法，理论上不可达
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
