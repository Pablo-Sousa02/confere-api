package com.pablosousa.confere.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="recuperacoes_senha")
public class RecuperacaoSenha {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="usuario_id", nullable=false) private Long usuarioId;
    @Column(name="token_hash", nullable=false, unique=true, length=64) private String tokenHash;
    @Column(name="expira_em", nullable=false) private Instant expiraEm;
    @Column(name="usado_em") private Instant usadoEm;
    @Column(name="versao_usuario", nullable=false) private long versaoUsuario;
    protected RecuperacaoSenha() {}
    public RecuperacaoSenha(Long usuarioId, String hash, Instant expiraEm, long versaoUsuario) {
        this.usuarioId = usuarioId; this.tokenHash = hash; this.expiraEm = expiraEm; this.versaoUsuario = versaoUsuario;
    }
    public Long getUsuarioId() { return usuarioId; }
    public long getVersaoUsuario() { return versaoUsuario; }
    public boolean disponivel(Instant agora) { return usadoEm == null && expiraEm.isAfter(agora); }
    public void consumir(Instant agora) { usadoEm = agora; }
}
