package br.gestaofinanceira.app.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import br.gestaofinanceira.app.GestaoFinanceiraApp;
import br.gestaofinanceira.app.R;
import br.gestaofinanceira.app.data.local.SessionManager;
import br.gestaofinanceira.app.data.remote.ApiErros;
import br.gestaofinanceira.app.data.remote.dto.LoginRequest;
import br.gestaofinanceira.app.data.remote.dto.LoginResponse;
import br.gestaofinanceira.app.ui.projetos.ProjetosActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** GP-01: autenticação. Com sessão válida, vai direto para a seleção de projeto. */
public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_SESSAO_EXPIRADA = "sessao_expirada";

    private SessionManager sessao;
    private TextInputLayout campoEmail;
    private TextInputLayout campoSenha;
    private TextInputEditText email;
    private TextInputEditText senha;
    private MaterialButton entrar;
    private ProgressBar progresso;
    private TextView erro;
    private Call<LoginResponse> chamada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        GestaoFinanceiraApp app = GestaoFinanceiraApp.de(this);
        sessao = app.getSessao();
        if (sessao.estaLogado()) {
            abrirProjetos();
            return;
        }

        setContentView(R.layout.activity_login);
        campoEmail = findViewById(R.id.campo_email);
        campoSenha = findViewById(R.id.campo_senha);
        email = findViewById(R.id.email);
        senha = findViewById(R.id.senha);
        entrar = findViewById(R.id.entrar);
        progresso = findViewById(R.id.progresso);
        erro = findViewById(R.id.erro);

        if (getIntent().getBooleanExtra(EXTRA_SESSAO_EXPIRADA, false)) {
            mostrarErro(getString(R.string.sessao_expirada));
        }
        entrar.setOnClickListener(v -> tentarLogin());
        senha.setOnEditorActionListener((v, acao, evento) -> {
            if (acao == EditorInfo.IME_ACTION_DONE) {
                tentarLogin();
                return true;
            }
            return false;
        });
    }

    private void tentarLogin() {
        String textoEmail = texto(email).trim();
        String textoSenha = texto(senha);
        campoEmail.setError(null);
        campoSenha.setError(null);
        erro.setVisibility(View.GONE);

        boolean valido = true;
        if (!Patterns.EMAIL_ADDRESS.matcher(textoEmail).matches()) {
            campoEmail.setError(getString(R.string.erro_email_invalido));
            valido = false;
        }
        if (TextUtils.isEmpty(textoSenha)) {
            campoSenha.setError(getString(R.string.erro_senha_vazia));
            valido = false;
        }
        if (!valido) return;

        carregando(true);
        chamada = GestaoFinanceiraApp.de(this).getApi().login(new LoginRequest(textoEmail, textoSenha));
        chamada.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> resposta) {
                if (isFinishing() || isDestroyed()) return;
                carregando(false);
                LoginResponse corpo = resposta.body();
                if (resposta.isSuccessful() && corpo != null && corpo.token != null) {
                    sessao.salvar(corpo.token, corpo.expiraEm, corpo.usuario.nome, corpo.usuario.email);
                    abrirProjetos();
                } else {
                    mostrarErro(ApiErros.mensagem(resposta));
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable falha) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) return;
                carregando(false);
                mostrarErro(ApiErros.mensagem(falha));
            }
        });
    }

    private void carregando(boolean ativo) {
        progresso.setVisibility(ativo ? View.VISIBLE : View.GONE);
        entrar.setEnabled(!ativo);
        email.setEnabled(!ativo);
        senha.setEnabled(!ativo);
    }

    private void mostrarErro(String mensagem) {
        erro.setText(mensagem);
        erro.setVisibility(View.VISIBLE);
    }

    private void abrirProjetos() {
        startActivity(new Intent(this, ProjetosActivity.class));
        finish();
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString();
    }

    @Override
    protected void onDestroy() {
        if (chamada != null) chamada.cancel();
        super.onDestroy();
    }
}
