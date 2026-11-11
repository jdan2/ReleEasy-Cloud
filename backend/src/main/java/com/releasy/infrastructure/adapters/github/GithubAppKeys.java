package com.releasy.infrastructure.adapters.github;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Lee la clave privada de la GitHub App.
 *
 * <p>GitHub la entrega en PEM PKCS#1 ({@code BEGIN RSA PRIVATE KEY}), pero Java solo lee PKCS#8
 * ({@code BEGIN PRIVATE KEY}). Se aceptan los dos formatos: PKCS#1 se envuelve en la estructura ASN.1 de
 * PKCS#8, sin depender de BouncyCastle ni de convertirla antes con {@code openssl}.
 */
public final class GithubAppKeys {

    /** AlgorithmIdentifier de rsaEncryption (OID 1.2.840.113549.1.1.1) con parámetros NULL, en DER. */
    private static final byte[] RSA_ALGORITHM_ID = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
    private static final byte[] VERSION_0 = {0x02, 0x01, 0x00};
    private static final byte SEQUENCE = 0x30;
    private static final byte OCTET_STRING = 0x04;

    private GithubAppKeys() {
    }

    public static RSAPrivateKey parsePrivateKey(String pem) {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(pkcs1 ? wrapPkcs1(der) : der));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("La clave privada de la GitHub App no es una clave RSA válida", e);
        }
    }

    /** PrivateKeyInfo ::= SEQUENCE { version 0, AlgorithmIdentifier, OCTET STRING (clave PKCS#1) } */
    static byte[] wrapPkcs1(byte[] pkcs1) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(VERSION_0);
        body.writeBytes(RSA_ALGORITHM_ID);
        body.write(OCTET_STRING);
        body.writeBytes(derLength(pkcs1.length));
        body.writeBytes(pkcs1);

        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.write(SEQUENCE);
        info.writeBytes(derLength(body.size()));
        info.writeBytes(body.toByteArray());
        return info.toByteArray();
    }

    /** Longitud DER: un byte si es menor que 128; si no, 0x80 + número de bytes, y los bytes. */
    static byte[] derLength(int length) {
        if (length < 0x80) {
            return new byte[] {(byte) length};
        }
        int bytes = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
        byte[] encoded = new byte[bytes + 1];
        encoded[0] = (byte) (0x80 | bytes);
        for (int i = 0; i < bytes; i++) {
            encoded[bytes - i] = (byte) (length >>> (8 * i));
        }
        return encoded;
    }
}
