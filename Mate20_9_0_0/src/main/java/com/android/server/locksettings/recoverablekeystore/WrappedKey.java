package com.android.server.locksettings.recoverablekeystore;

import android.util.Log;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class WrappedKey {
    private static final String APPLICATION_KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String KEY_WRAP_CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String TAG = "WrappedKey";
    private final byte[] mKeyMaterial;
    private final byte[] mNonce;
    private final int mPlatformKeyGenerationId;
    private final int mRecoveryStatus;

    public static WrappedKey fromSecretKey(PlatformEncryptionKey wrappingKey, SecretKey key) throws InvalidKeyException, KeyStoreException {
        if (key.getEncoded() != null) {
            try {
                Cipher cipher = Cipher.getInstance(KEY_WRAP_CIPHER_ALGORITHM);
                cipher.init(3, wrappingKey.getKey());
                try {
                    return new WrappedKey(cipher.getIV(), cipher.wrap(key), wrappingKey.getGenerationId(), 1);
                } catch (IllegalBlockSizeException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof KeyStoreException) {
                        throw ((KeyStoreException) cause);
                    }
                    throw new RuntimeException("IllegalBlockSizeException should not be thrown by AES/GCM/NoPadding mode.", e);
                }
            } catch (NoSuchAlgorithmException | NoSuchPaddingException e2) {
                throw new RuntimeException("Android does not support AES/GCM/NoPadding. This should never happen.");
            }
        }
        throw new InvalidKeyException("key does not expose encoded material. It cannot be wrapped.");
    }

    public WrappedKey(byte[] nonce, byte[] keyMaterial, int platformKeyGenerationId) {
        this.mNonce = nonce;
        this.mKeyMaterial = keyMaterial;
        this.mPlatformKeyGenerationId = platformKeyGenerationId;
        this.mRecoveryStatus = 1;
    }

    public WrappedKey(byte[] nonce, byte[] keyMaterial, int platformKeyGenerationId, int recoveryStatus) {
        this.mNonce = nonce;
        this.mKeyMaterial = keyMaterial;
        this.mPlatformKeyGenerationId = platformKeyGenerationId;
        this.mRecoveryStatus = recoveryStatus;
    }

    public byte[] getNonce() {
        return this.mNonce;
    }

    public byte[] getKeyMaterial() {
        return this.mKeyMaterial;
    }

    public int getPlatformKeyGenerationId() {
        return this.mPlatformKeyGenerationId;
    }

    public int getRecoveryStatus() {
        return this.mRecoveryStatus;
    }

    public static Map<String, SecretKey> unwrapKeys(PlatformDecryptionKey platformKey, Map<String, WrappedKey> wrappedKeys) throws NoSuchAlgorithmException, NoSuchPaddingException, BadPlatformKeyException, InvalidKeyException, InvalidAlgorithmParameterException {
        HashMap<String, SecretKey> unwrappedKeys = new HashMap();
        Cipher cipher = Cipher.getInstance(KEY_WRAP_CIPHER_ALGORITHM);
        int platformKeyGenerationId = platformKey.getGenerationId();
        for (String alias : wrappedKeys.keySet()) {
            WrappedKey wrappedKey = (WrappedKey) wrappedKeys.get(alias);
            if (wrappedKey.getPlatformKeyGenerationId() == platformKeyGenerationId) {
                cipher.init(4, platformKey.getKey(), new GCMParameterSpec(128, wrappedKey.getNonce()));
                try {
                    unwrappedKeys.put(alias, (SecretKey) cipher.unwrap(wrappedKey.getKeyMaterial(), APPLICATION_KEY_ALGORITHM, 3));
                } catch (InvalidKeyException | NoSuchAlgorithmException e) {
                    Log.e(TAG, String.format(Locale.US, "Error unwrapping recoverable key with alias '%s'", new Object[]{alias}), e);
                }
            } else {
                throw new BadPlatformKeyException(String.format(Locale.US, "WrappedKey with alias '%s' was wrapped with platform key %d, not platform key %d", new Object[]{alias, Integer.valueOf(wrappedKey.getPlatformKeyGenerationId()), Integer.valueOf(platformKey.getGenerationId())}));
            }
        }
        return unwrappedKeys;
    }
}
