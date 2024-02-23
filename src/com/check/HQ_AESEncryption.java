package com.check;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class   HQ_AESEncryption {

    public static String encrypt(String plainText, String key) throws Exception {
        // Truncate or pad the key to 16 bytes
        byte[] keyBytes = new byte[16];
        System.arraycopy(key.getBytes(), 0, keyBytes, 0, Math.min(key.getBytes().length, 16));

        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String decrypt(String encryptedText, String key) throws Exception {
        // Truncate or pad the key to 16 bytes
        byte[] keyBytes = new byte[16];
        System.arraycopy(key.getBytes(), 0, keyBytes, 0, Math.min(key.getBytes().length, 16));

        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }


}
