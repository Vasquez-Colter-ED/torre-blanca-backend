package pe.torreblanca.backend.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // SIN @Async temporalmente para ver el error real en los logs de Render
    public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Torre Blanca");
            helper.setTo(destinatario);
            helper.setSubject("Código de recuperación — Torre Blanca");
            helper.setText(buildEmailHtml(nombre, codigo), true);
            mailSender.send(message);
            System.out.println("✅ Correo enviado exitosamente a: " + destinatario);
        } catch (Exception e) {
            System.err.println("❌ ERROR enviando correo a " + destinatario + ": " + e.getMessage());
            System.err.println("❌ Causa: " + e.getCause());
            throw new RuntimeException("Error de email: " + e.getMessage());
        }
    }

    private String buildEmailHtml(String nombre, String codigo) {
        StringBuilder digitosHtml = new StringBuilder();
        for (char c : codigo.toCharArray()) {
            digitosHtml.append(String.format(
                "<td style='padding:0 6px;'><div style='width:48px;height:56px;background:#F8FAFC;border:2px solid #E2E8F0;border-radius:10px;font-size:28px;font-weight:800;color:#0F172A;text-align:center;line-height:56px;'>%s</div></td>", c));
        }
        return String.format("""
        <!DOCTYPE html><html lang="es"><head><meta charset="UTF-8"></head>
        <body style="margin:0;padding:0;background:#F1F5F9;font-family:sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
            <tr><td align="center">
              <table width="520" cellpadding="0" cellspacing="0" style="background:#fff;border-radius:16px;">
                <tr><td style="background:#0F172A;padding:32px 40px;border-radius:16px 16px 0 0;">
                  <div style="color:#fff;font-size:18px;font-weight:800;">Torre Blanca</div>
                </td></tr>
                <tr><td style="padding:40px;">
                  <p style="color:#475569;">Hola, <strong>%s</strong></p>
                  <h2 style="color:#0F172A;">Tu código de recuperación</h2>
                  <table cellpadding="0" cellspacing="0" style="margin:24px auto;"><tr>%s</tr></table>
                  <p style="color:#1D4ED8;font-weight:600;">⏱ Expira en 15 minutos.</p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body></html>
        """, nombre, digitosHtml.toString());
    }
}
