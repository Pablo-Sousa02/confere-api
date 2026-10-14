package com.pablosousa.confere.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import java.net.URI;

@Component
public class EmailRecuperacao {
    private final JavaMailSender sender;
    private final String origem, url;
    public EmailRecuperacao(ObjectProvider<JavaMailSender> provider, @Value("${app.email.from}") String origem,
                            @Value("${app.reset.url}") String url, @Value("${app.reset.enabled:true}") boolean enabled) {
        this.origem = origem; this.url = url;
        if (!enabled) { this.sender = null; return; }
        JavaMailSender sender = provider.getIfAvailable();
        if (sender == null || origem.isBlank() || url.isBlank())
            throw new IllegalStateException("Recuperação habilitada exige SMTP_HOST, EMAIL_FROM e RESET_PASSWORD_URL.");
        URI uri = URI.create(url);
        if (uri.getHost() == null || uri.getRawQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                && java.util.Set.of("localhost", "127.0.0.1").contains(uri.getHost()))))
            throw new IllegalStateException("RESET_PASSWORD_URL deve ser HTTPS ou HTTP de loopback, sem query/fragmento.");
        this.sender = sender;
    }
    public boolean disponivel() { return sender != null; }
    public void enviar(String destino, String token) {
        if (!disponivel()) throw new IllegalStateException("Recuperação por e-mail desabilitada.");
        var mensagem = new SimpleMailMessage();
        mensagem.setFrom(origem); mensagem.setTo(destino); mensagem.setSubject("Confere: recuperação de senha");
        // Fragmentos não são enviados em access logs HTTP. O futuro frontend deve removê-lo após leitura.
        mensagem.setText("Para redefinir sua senha, use o link abaixo em até 20 minutos.\n"
                + url + "#token=" + token + "\nSe você não solicitou, ignore esta mensagem.");
        sender.send(mensagem);
    }
}
