package br.gestaofinanceira.app.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Instant;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda a sessão do usuário. O token JWT é cifrado com AES-GCM usando uma chave
 * do Android Keystore, que não sai do dispositivo; a senha nunca é armazenada.
 */
public class SessionManager {

    private static final String ARQUIVO = "sessao";
    private static final String ALIAS_CHAVE = "gestao_financeira_sessao";
    private static final String CHAVE_TOKEN = "token";
    private static final String CHAVE_EXPIRA_EM = "expira_em";
    private static final String CHAVE_NOME = "nome";
    private static final String CHAVE_EMAIL = "email";
    private static final int TAMANHO_IV = 12;
    private static final int TAMANHO_TAG = 128;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE);
    }

    public void salvar(String token, String expiraEm, String nome, String email) {
        prefs.edit()
                .putString(CHAVE_TOKEN, cifrar(token))
                .putString(CHAVE_EXPIRA_EM, expiraEm)
                .putString(CHAVE_NOME, nome)
                .putString(CHAVE_EMAIL, email)
                .apply();
    }

    /** Retorna o token se existir e ainda não tiver expirado; caso contrário, null. */
    public synchronized String getToken() {
        String cifrado = prefs.getString(CHAVE_TOKEN, null);
        String expiraEm = prefs.getString(CHAVE_EXPIRA_EM, null);
        if (cifrado == null || expiraEm == null) return null;
        try {
            if (!Instant.parse(expiraEm).isAfter(Instant.now())) {
                limpar();
                return null;
            }
            return decifrar(cifrado);
        } catch (Exception e) {
            limpar();
            return null;
        }
    }

    public boolean estaLogado() {
        return getToken() != null;
    }

    public String getNome() {
        return prefs.getString(CHAVE_NOME, "");
    }

    public String getEmail() {
        return prefs.getString(CHAVE_EMAIL, "");
    }

    public synchronized void limpar() {
        prefs.edit().clear().apply();
    }

    private SecretKey chave() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(ALIAS_CHAVE)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(ALIAS_CHAVE, null)).getSecretKey();
        }
        KeyGenerator gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gerador.init(new KeyGenParameterSpec.Builder(ALIAS_CHAVE,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gerador.generateKey();
    }

    private String cifrar(String texto) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave());
            byte[] iv = cipher.getIV();
            byte[] dados = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + dados.length).put(iv).put(dados);
            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível proteger a sessão", e);
        }
    }

    private String decifrar(String base64) throws Exception {
        byte[] tudo = Base64.decode(base64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, chave(), new GCMParameterSpec(TAMANHO_TAG, tudo, 0, TAMANHO_IV));
        byte[] texto = cipher.doFinal(tudo, TAMANHO_IV, tudo.length - TAMANHO_IV);
        return new String(texto, StandardCharsets.UTF_8);
    }
}
