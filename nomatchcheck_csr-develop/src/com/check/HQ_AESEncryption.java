package com.check;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class HQ_AESEncryption {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    private HQ_AESEncryption() {
    // instead of public one
    }
    public static String encrypt(String plainText, String key){
        
    	// 生成隨機 IV
		byte[] iv;
		byte[] encryptedBytes;
		try {
			// 使用 SHA-256 生成 256 位密鑰
			byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

			iv = new byte[GCM_IV_LENGTH];
			new SecureRandom().nextBytes(iv);

			// 使用 GCM 模式
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
			cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, parameterSpec);

			encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
	        // 將 IV 和加密後的數據組合		
	        byte[] combined = new byte[iv.length + encryptedBytes.length];
	        System.arraycopy(iv, 0, combined, 0, iv.length);
	        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

	        return Base64.getEncoder().encodeToString(combined);    
	        
		} catch (InvalidKeyException 
				| NoSuchAlgorithmException 
				| NoSuchPaddingException 
				| InvalidAlgorithmParameterException
				| IllegalBlockSizeException
				| BadPaddingException e) {
            // TODO Auto-generated catch block
            return e.getMessage();     
        }
    }


    public static String decrypt(String encryptedText, String key) {
        try {
			// 使用 SHA-256 生成 256 位密鑰
			byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

			// 解碼 Base64 字符串
			byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);

			// 提取 IV
			byte[] iv = new byte[GCM_IV_LENGTH];
			System.arraycopy(decodedBytes, 0, iv, 0, GCM_IV_LENGTH);

			// 提取加密數據
			byte[] encryptedData = new byte[decodedBytes.length - GCM_IV_LENGTH];
			System.arraycopy(decodedBytes, GCM_IV_LENGTH, encryptedData, 0, encryptedData.length);

			// 使用 GCM 模式
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, parameterSpec);

			// 解密
			byte[] decryptedBytes = cipher.doFinal(encryptedData);

			return new String(decryptedBytes, StandardCharsets.UTF_8);
		} catch (InvalidKeyException 
				| NoSuchAlgorithmException 
				| NoSuchPaddingException 
				| InvalidAlgorithmParameterException
				| IllegalBlockSizeException
				| BadPaddingException e) {
            // TODO Auto-generated catch block
            return e.getMessage();            
        }
    }


}
