package com.digitalalu.alu.util;

import java.security.MessageDigest;
import java.security.SecureRandom;

/** Small crypto helpers — PINs are never stored in plain text anymore. */
public final class SecurityUtil {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private SecurityUtil() {}

    /** SHA-256 of the input, hex encoded. */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Random 16-byte salt, hex encoded. */
    public static String randomSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        StringBuilder sb = new StringBuilder(salt.length * 2);
        for (byte b : salt) {
            sb.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return sb.toString();
    }

    /** Constant-ish time comparison to avoid trivial timing checks. */
    public static boolean equals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
