package com.pablosousa.confere.usuario;
import com.pablosousa.confere.empresa.Empresa;
import jakarta.persistence.*;
@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(nullable = false, length = 150) private String nome;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "senha_hash", nullable = false, length = 100) private String senhaHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Papel papel;
    @Column(nullable = false) private boolean ativo = true;
    @Column(name = "versao_token", nullable = false) private long versaoToken;
    protected Usuario() {}
    public Usuario(Empresa empresa, String nome, String email, String senhaHash, Papel papel) {
        this.empresa = empresa; this.nome = nome; this.email = email; this.senhaHash = senhaHash; this.papel = papel;
    }
    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public boolean isAtivo() { return ativo; }
    public long getVersaoToken() { return versaoToken; }
    public void redefinirSenha(String hash) { senhaHash = hash; versaoToken++; }
    public void atualizar(String nome, Papel papel, boolean ativo, String senhaHash) {
        this.nome = nome; this.papel = papel; this.ativo = ativo;
        if (senhaHash != null) this.senhaHash = senhaHash;
        versaoToken++;
    }
}
