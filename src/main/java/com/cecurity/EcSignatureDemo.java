package com.cecurity;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EcSignatureDemo {

    public static void main(String[] args) throws Exception {
        String message = "Hello, elliptic curve signatures!";

        KeyPair keyPair = generateEcKeyPair();

        String publicKeyHex = publicKeyToHex(keyPair.getPublic());
        byte[] signature = signMessage(message, keyPair.getPrivate());

        boolean valid = verifySignature(message, signature, publicKeyHex);

        System.out.println("Message: " + message);
        System.out.println("Public Key Hex: " + publicKeyHex);
        System.out.println("Signature (Base64): " + Base64.getEncoder().encodeToString(signature));
        System.out.println("Signature valid: " + valid);
    }

    public static KeyPair generateEcKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256);
        return keyPairGenerator.generateKeyPair();
    }

    public static byte[] signMessage(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    public static boolean verifySignature(String message, byte[] signatureBytes, String publicKeyHex) throws Exception {
        PublicKey publicKey = hexToPublicKey(publicKeyHex);

        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signatureBytes);
    }

    public static String publicKeyToHex(PublicKey publicKey) {
        return bytesToHex(publicKey.getEncoded());
    }

    public static PublicKey hexToPublicKey(String publicKeyHex) throws Exception {
        byte[] encodedKey = hexToBytes(publicKeyHex);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(keySpec);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }
}

