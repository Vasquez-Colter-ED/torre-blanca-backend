package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class MercadoPagoService {

    @Value("${mercadopago.access.token}")
    private String accessToken;

    @Autowired private CuotaMantenimientoRepository cuotaRepository;
    @Autowired private PagoMantenimientoRepository pagoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private BoletasService boletasService;

    private final RestTemplate restTemplate = new RestTemplate();

    public MensajeResponse procesarPago(PagoMercadoPagoRequest request, Integer solicitanteId) {
        CuotaMantenimiento cuota = cuotaRepository.findById(request.getCuotaId())
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        if (cuota.getEstado() == EstadoCuota.PAGADO)
            throw new RuntimeException("Esta cuota ya está pagada");

        boolean esDirectivo = esDirectivo(solicitanteId);
        Integer pagadorId = esDirectivo && request.getPagadorId() != null
                ? request.getPagadorId() : solicitanteId;

        Usuario pagador = usuarioRepository.findById(pagadorId)
                .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));

        Map<String, Object> mpRequest = Map.of(
            "transaction_amount", cuota.getMontoCalculado().doubleValue(),
            "token",              request.getToken(),
            "description",       "Cuota mantenimiento Depto " + cuota.getDepartamento().getNumero(),
            "installments",      request.getCuotas() != null ? request.getCuotas() : 1,
            "payment_method_id", request.getMetodoPago(),
            "payer",             Map.of("email", request.getEmail())
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);

        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(mpRequest, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.mercadopago.com/v1/payments", httpRequest, Map.class);

            Map<String, Object> body = response.getBody();
            String status = (String) body.get("status");
            System.out.println("[MP] Status: " + status + " | Response: " + body);

            if ("approved".equals(status)) {
                PagoMantenimiento pago = new PagoMantenimiento();
                pago.setCuota(cuota);
                pago.setPagador(pagador);
                pago.setMonto(cuota.getMontoCalculado());
                pago.setFechaPago(LocalDateTime.now());
                pago.setMetodoPago(MetodoPago.TRANSFERENCIA);
                pago.setNumeroOperacion(body.get("id").toString());
                pago.setObservaciones("Pago con tarjeta via Mercado Pago");
                pago.setEstado(EstadoPago.VERIFICADO);
                pago.setRegistradoPor("RESIDENTE");
                pagoRepository.save(pago);

                cuota.setEstado(EstadoCuota.PAGADO);
                cuotaRepository.save(cuota);

                Usuario admin = usuarioRepository.findById(solicitanteId).orElse(pagador);
                boletasService.generarBoleta(pago, admin);

                String nombre = pagador.getNombre() + " " + pagador.getApellido();
                return new MensajeResponse("Pago aprobado. Boleta generada a nombre de " + nombre, true);
            } else if ("in_process".equals(status) || "pending".equals(status)) {
                return new MensajeResponse("El pago está en proceso. Te notificaremos cuando se confirme.", true);
            } else {
                throw new RuntimeException("Pago rechazado por la entidad emisora. Verifica los datos de tu tarjeta.");
            }
        } catch (HttpClientErrorException e) {
            System.err.println("[MP ERROR] HTTP " + e.getStatusCode() + " | Body: " + e.getResponseBodyAsString());
            throw new RuntimeException("Error MP: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            System.err.println("[MP ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("rechazado")) throw e;
            throw new RuntimeException("Error al procesar el pago: " + e.getMessage());
        }
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }
}
