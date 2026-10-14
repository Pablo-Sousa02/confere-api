package com.pablosousa.confere.usuario;
import com.pablosousa.confere.auth.*;
import com.pablosousa.confere.empresa.EmpresaRepository;
import com.pablosousa.confere.shared.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
@Service @PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final EmpresaAtual atual;
    private final PasswordEncoder encoder;
    public UsuarioService(UsuarioRepository usuarios, EmpresaRepository empresas, EmpresaAtual atual, PasswordEncoder encoder) {
        this.usuarios = usuarios; this.empresas = empresas; this.atual = atual; this.encoder = encoder;
    }
    @Transactional(readOnly=true)
    public Pagina<UsuarioDtos.Resumo> listar(int page, int size) {
        return Pagina.de(usuarios.findAllByEmpresa_Id(atual.id(), Pagina.pedido(page, size)).map(UsuarioDtos.Resumo::de));
    }
    @Transactional
    public UsuarioDtos.Resumo criar(UsuarioDtos.Criar dto) {
        var empresa = empresas.bloquear(atual.id()).orElseThrow(NaoEncontradoException::new);
        var usuario = new Usuario(empresa, Credenciais.nome(dto.nome()), Credenciais.email(dto.email()),
                encoder.encode(Credenciais.senha(dto.senha())), dto.papel());
        if (usuarios.existsByEmail(usuario.getEmail())) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "E-mail indisponível.");
        return UsuarioDtos.Resumo.de(usuarios.saveAndFlush(usuario));
    }
    @Transactional
    public UsuarioDtos.Resumo atualizar(Long id, UsuarioDtos.Atualizar dto) {
        empresas.bloquear(atual.id()).orElseThrow(NaoEncontradoException::new);
        var usuario = usuarios.findByIdAndEmpresa_Id(id, atual.id()).orElseThrow(NaoEncontradoException::new);
        if (usuario.isAtivo() && usuario.getPapel() == Papel.ADMIN && (!dto.ativo() || dto.papel() != Papel.ADMIN)
                && usuarios.countByEmpresa_IdAndPapelAndAtivoTrue(atual.id(), Papel.ADMIN) <= 1)
            throw new IllegalArgumentException("Mantenha pelo menos um administrador ativo na empresa.");
        usuario.atualizar(Credenciais.nome(dto.nome()), dto.papel(), dto.ativo(),
                dto.senha() == null ? null : encoder.encode(Credenciais.senha(dto.senha())));
        return UsuarioDtos.Resumo.de(usuario);
    }
}
