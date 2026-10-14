package com.pablosousa.confere.empresa;
import jakarta.persistence.*;
@Entity
@Table(name = "empresas")
public class Empresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 150) private String nome;
    @Column(nullable = false) private boolean inicial;
    protected Empresa() {}
    public Empresa(String nome) { this.nome = nome; }
    public Long getId() { return id; }
    public String getNome() { return nome; }
}
