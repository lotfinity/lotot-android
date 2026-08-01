package com.fr3ts0n.ecu.gui.androbd;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the MQTT password encrypted with a non-exportable Android Keystore key. */
final class LotoTSecureCredentialStore
{
    private static final String STORE_NAME = "lotot_secure_credentials";
    private static final String KEY_ALIAS = "lotot_mqtt_credentials_v1";
    private static final String PASSWORD_IV = "mqtt_password_iv";
    private static final String PASSWORD_CIPHERTEXT = "mqtt_password_ciphertext";

    private final SharedPreferences storage;

    LotoTSecureCredentialStore(Context context)
    {
        storage = context.getApplicationContext().getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
    }

    synchronized boolean putPassword(String password)
    {
        String value = password == null ? "" : password;
        if (value.isEmpty())
        {
            clearPassword();
            return true;
        }
        try
        {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            storage.edit()
                    .putString(PASSWORD_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .putString(PASSWORD_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .apply();
            return true;
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    synchronized String getPassword()
    {
        String encodedIv = storage.getString(PASSWORD_IV, "");
        String encodedCiphertext = storage.getString(PASSWORD_CIPHERTEXT, "");
        if (encodedIv == null || encodedIv.isEmpty()
                || encodedCiphertext == null || encodedCiphertext.isEmpty()) return "";
        try
        {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(encodedIv, Base64.NO_WRAP)));
            byte[] clear = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP));
            return new String(clear, StandardCharsets.UTF_8);
        }
        catch (Exception ignored)
        {
            clearPassword();
            return "";
        }
    }

    synchronized boolean hasPassword()
    {
        return !getPassword().isEmpty();
    }

    synchronized boolean migrateLegacyPassword(SharedPreferences legacy, String legacyKey)
    {
        String password = legacy.getString(legacyKey, "");
        if (password == null || password.isEmpty()) return true;
        if (!putPassword(password)) return false;
        legacy.edit().remove(legacyKey).apply();
        return true;
    }

    synchronized void clearPassword()
    {
        storage.edit().remove(PASSWORD_IV).remove(PASSWORD_CIPHERTEXT).apply();
    }

    private SecretKey getOrCreateKey() throws Exception
    {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
