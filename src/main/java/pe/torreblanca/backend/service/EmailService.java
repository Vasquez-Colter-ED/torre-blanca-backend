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

    public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Torre Blanca");
            helper.setTo(destinatario);
            helper.setSubject("Código de recuperación — Torre Blanca");
            helper.setText(buildEmailHtml(nombre, codigo), true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo enviar el correo. Intenta nuevamente.");
        }
    }

    private String buildEmailHtml(String nombre, String codigo) {
        StringBuilder digitosHtml = new StringBuilder();
        for (char c : codigo.toCharArray()) {
            digitosHtml.append(String.format(
                "<td style='padding:0 6px;'><div style='width:48px;height:56px;background:#F8FAFC;border:2px solid #E2E8F0;border-radius:10px;font-size:28px;font-weight:800;color:#0F172A;text-align:center;line-height:56px;'>%s</div></td>", c));
        }
        return String.format("""
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"></head>
        <body style="margin:0;padding:0;background:#F1F5F9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#F1F5F9;padding:40px 20px;">
            <tr><td align="center">
              <table width="520" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">
                <tr><td style="background:#0F172A;padding:32px 40px;">
                  <table cellpadding="0" cellspacing="0"><tr>
                    <td><div style="width:44px;height:44px;background:#2563EB;border-radius:10px;color:#fff;font-size:14px;font-weight:800;text-align:center;line-height:44px;">TB</div></td>
                    <td style="padding-left:14px;">
                      <div style="color:#fff;font-size:18px;font-weight:800;">Torre Blanca</div>
                      <div style="color:rgba(255,255,255,0.5);font-size:12px;">Sistema de Administración Residencial</div>
                    </td>
                  </tr></table>
                </td></tr>
                <tr><td style="padding:40px;">
                  <p style="font-size:15px;color:#475569;margin:0 0 6px 0;">Hola, <strong style="color:#0F172A;">%s</strong></p>
                  <h2 style="font-size:22px;font-weight:800;color:#0F172A;margin:0 0 12px 0;">Tu código de recuperación</h2>
                  <p style="font-size:14px;color:#64748B;margin:0 0 32px 0;line-height:1.6;">Usa el siguiente código para restablecer tu contraseña:</p>
                  <table cellpadding="0" cellspacing="0" style="margin:0 auto 32px auto;"><tr>%s</tr></table>
                  <div style="background:#EFF6FF;border:1px solid #BFDBFE;border-radius:10px;padding:14px 18px;margin-bottom:28px;">
                    <p style="margin:0;font-size:13px;color:#1D4ED8;font-weight:600;">⏱ Este código expira en <strong>15 minutos</strong>.</p>
                  </div>
                  <p style="font-size:13px;color:#94A3B8;margin:0;">Si no solicitaste este código, ignora este correo.</p>
                </td></tr>
                <tr><td style="background:#F8FAFC;padding:20px 40px;border-top:1px solid #E2E8F0;">
                  <p style="margin:0;font-size:12px;color:#94A3B8;text-align:center;">Residencial Torre Blanca · Chiclayo, Perú</p>
                </td></tr>
              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """, nombre, digitosHtml.toString());
    }
}
