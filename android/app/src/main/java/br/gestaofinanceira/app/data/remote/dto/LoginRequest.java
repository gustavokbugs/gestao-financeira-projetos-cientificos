package br.gestaofinanceira.app.data.remote.dto;

public class LoginRequest {
    public final String email;
    public final String senha;

    public LoginRequest(String email, String senha) {
        this.email = email;
        this.senha = senha;
    }
}
