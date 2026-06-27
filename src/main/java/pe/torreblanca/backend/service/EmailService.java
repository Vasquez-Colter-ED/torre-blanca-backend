package pe.torreblanca.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public void enviarCodigoRecuperacion(String destinatario, String nombre, String codigo) {
        try {
            System.out.println("[EMAIL] Enviando via Brevo a: " + destinatario);

            Map<String, Object> body = Map.of(
                "sender",      Map.of("name", "Torre Blanca", "email", senderEmail),
                "to",          List.of(Map.of("email", destinatario, "name", nombre)),
                "subject",     "Código de recuperación — Torre Blanca",
                "htmlContent", buildEmailHtml(nombre, codigo)
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://api.brevo.com/v3/smtp/email", request, String.class);

            System.out.println("[EMAIL] Brevo respondio: HTTP " + response.getStatusCode() + " | " + response.getBody());

        } catch (HttpClientErrorException e) {
            System.err.println("[EMAIL] Error Brevo: HTTP " + e.getStatusCode() + " | " + e.getResponseBodyAsString());
            throw new RuntimeException("No se pudo enviar el correo: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("[EMAIL] Error inesperado: " + e.getMessage());
            throw new RuntimeException("No se pudo enviar el correo: " + e.getMessage());
        }
    }

    private String buildEmailHtml(String nombre, String codigo) {
        StringBuilder digitosHtml = new StringBuilder();
        for (char c : codigo.toCharArray()) {
            digitosHtml.append(String.format(
                "<td style='padding:0 6px;'>" +
                "<div style='width:48px;height:56px;background:#F8FAFC;border:2px solid #E2E8F0;" +
                "border-radius:10px;font-size:28px;font-weight:800;color:#0F172A;" +
                "text-align:center;line-height:56px;font-family:monospace;'>%s</div></td>", c));
        }

        return String.format("""
        <!DOCTYPE html>
        <html lang="es">
        <head><meta charset="UTF-8"></head>
        <body style="margin:0;padding:0;background:#F1F5F9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;">
          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#F1F5F9;padding:40px 20px;">
            <tr><td align="center">
              <table width="520" cellpadding="0" cellspacing="0"
                style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(15,23,42,0.08);">

                <tr><td style="background:#0F172A;padding:32px 40px;">
                  <table cellpadding="0" cellspacing="0"><tr>
                    <td><div style="width:44px;height:44px;background:#2563EB;border-radius:10px;
                      color:#fff;font-size:14px;font-weight:800;text-align:center;line-height:44px;">TB</div></td>
                    <td style="padding-left:14px;">
                      <div style="color:#fff;font-size:18px;font-weight:800;">Torre Blanca</div>
                      <div style="color:rgba(255,255,255,0.5);font-size:12px;">Sistema de Administracion Residencial</div>
                    </td>
                  </tr></table>
                </td></tr>

                <tr><td style="padding:40px;">
                  <p style="font-size:15px;color:#475569;margin:0 0 6px 0;">
                    Hola, <strong style="color:#0F172A;">%s</strong>
                  </p>
                  <h2 style="font-size:22px;font-weight:800;color:#0F172A;margin:0 0 12px 0;">
                    Codigo de recuperacion de contrasena
                  </h2>
                  <p style="font-size:14px;color:#64748B;margin:0 0 32px 0;line-height:1.6;">
                    Usa el siguiente codigo para restablecer tu contrasena en Torre Blanca:
                  </p>

                  <table cellpadding="0" cellspacing="0" style="margin:0 auto 32px auto;">
                    <tr>%s</tr>
                  </table>

                  <div style="background:#EFF6FF;border:1px solid #BFDBFE;border-radius:10px;
                    padding:14px 18px;margin-bottom:28px;">
                    <p style="margin:0;font-size:13px;color:#1D4ED8;font-weight:600;">
                      Este codigo expira en <strong>15 minutos</strong>.
                    </p>
                  </div>

                  <p style="font-size:13px;color:#94A3B8;margin:0;line-height:1.6;">
                    Si no solicitaste este codigo, puedes ignorar este correo con seguridad.
                  </p>
                </td></tr>

                <tr><td style="background:#F8FAFC;padding:20px 40px;border-top:1px solid #E2E8F0;">
                  <p style="margin:0;font-size:12px;color:#94A3B8;text-align:center;">
                    Residencial Torre Blanca · Chiclayo, Peru
                  </p>
                </td></tr>

              </table>
            </td></tr>
          </table>
        </body>
        </html>
        """, nombre, digitosHtml.toString());
    }
}
