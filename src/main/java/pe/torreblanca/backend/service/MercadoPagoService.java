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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    // Comisión MP Perú: 3.99% + S/ 0.30
    private BigDecimal calcularComision(BigDecimal monto) {
        return monto.multiply(new BigDecimal("0.0399"))
                .add(new BigDecimal("0.30"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ── Pago individual ──────────────────────────────────────────────
    public MensajeResponse procesarPago(PagoMercadoPagoRequest request, Integer solicitanteId) {
        CuotaMantenimiento cuota = cuotaRepository.findById(request.getCuotaId())
                .orElseThrow(() -> new RuntimeException("Cuota no encontrada"));

        if (cuota.getEstado() == EstadoCuota.PAGADO)
            throw new RuntimeException("Esta cuota ya está pagada");

        boolean esDir = esDirectivo(solicitanteId);
        Integer pagadorId = esDir && request.getPagadorId() != null
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
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

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
                pago.setComision(calcularComision(cuota.getMontoCalculado()));
                pago.setFechaPago(LocalDateTime.now());
                pago.setMetodoPago(MetodoPago.TRANSFERENCIA);
                pago.setNumeroOperacion(body.get("id").toString());
                pago.setObservaciones("Pago con tarjeta via Mercado Pago");
                pago.setEstado(EstadoPago.VERIFICADO);
                pago.setRegistradoPor(esDir ? "DIRECTIVO" : "RESIDENTE");
                // Snapshot de auditoría — si un directivo procesó el pago de otro
                // residente, guardamos quién fue y con qué cargo en ese momento
                if (esDir) {
                    Usuario registrante = usuarioRepository.findById(solicitanteId).orElse(null);
                    if (registrante != null) {
                        pago.setRegistradoPorUsuario(registrante);
                        pago.setRegistradoPorNombre(registrante.getNombre() + " " + registrante.getApellido());
                        pago.setRegistradoPorCargo(obtenerCargoActual(solicitanteId));
                    }
                }
                pagoRepository.save(pago);

                cuota.setEstado(EstadoCuota.PAGADO);
                cuota.setMontoPagado(cuota.getMontoCalculado());
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

    // ── Pago múltiple ────────────────────────────────────────────────
    public MensajeResponse procesarPagoMultiple(PagoMultipleRequest request, Integer solicitanteId) {
        if (request.getCuotaIds() == null || request.getCuotaIds().isEmpty())
            throw new RuntimeException("Debes seleccionar al menos una cuota");

        List<CuotaMantenimiento> cuotas = cuotaRepository.findAllById(request.getCuotaIds());
        if (cuotas.size() != request.getCuotaIds().size())
            throw new RuntimeException("Una o más cuotas no fueron encontradas");

        cuotas.forEach(c -> {
            if (c.getEstado() == EstadoCuota.PAGADO)
                throw new RuntimeException("La cuota de " + c.getConfiguracion().getMes()
                        + "/" + c.getConfiguracion().getAnio() + " ya está pagada");
        });

        Integer pagadorId = esDirectivo(solicitanteId) && request.getPagadorId() != null
                ? request.getPagadorId() : solicitanteId;
        Usuario pagador = usuarioRepository.findById(pagadorId)
                .orElseThrow(() -> new RuntimeException("Pagador no encontrado"));
        boolean esDirMultiple = esDirectivo(solicitanteId);

        BigDecimal total = cuotas.stream()
                .map(CuotaMantenimiento::getMontoCalculado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String descripcion = cuotas.size() == 1
                ? "Cuota mantenimiento Depto " + cuotas.get(0).getDepartamento().getNumero()
                : "Pago múltiple " + cuotas.size() + " cuotas - Depto " + cuotas.get(0).getDepartamento().getNumero();

        Map<String, Object> mpRequest = Map.of(
            "transaction_amount", total.doubleValue(),
            "token",              request.getToken(),
            "description",       descripcion,
            "installments",      request.getCuotas() != null ? request.getCuotas() : 1,
            "payment_method_id", request.getMetodoPago() != null ? request.getMetodoPago() : "visa",
            "payer",             Map.of("email", request.getEmail())
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("X-Idempotency-Key", UUID.randomUUID().toString());

        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(mpRequest, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://api.mercadopago.com/v1/payments", httpRequest, Map.class);

            Map<String, Object> body = response.getBody();
            String status = (String) body.get("status");
            System.out.println("[MP-MULTIPLE] Status: " + status + " | Total: " + total);

            if ("approved".equals(status)) {
                String operacionId = body.get("id").toString();
                List<PagoMantenimiento> pagos = new java.util.ArrayList<>();

                for (CuotaMantenimiento cuota : cuotas) {
                    PagoMantenimiento pago = new PagoMantenimiento();
                    pago.setCuota(cuota);
                    pago.setPagador(pagador);
                    pago.setMonto(cuota.getMontoCalculado());
                    pago.setComision(calcularComision(cuota.getMontoCalculado()));
                    pago.setFechaPago(LocalDateTime.now());
                    pago.setMetodoPago(MetodoPago.TRANSFERENCIA);
                    pago.setNumeroOperacion(operacionId);
                    pago.setObservaciones("Pago múltiple con tarjeta via Mercado Pago - " + cuotas.size() + " cuotas");
                    pago.setEstado(EstadoPago.VERIFICADO);
                    pago.setRegistradoPor(esDirMultiple ? "DIRECTIVO" : "RESIDENTE");
                    if (esDirMultiple) {
                        Usuario registrante = usuarioRepository.findById(solicitanteId).orElse(null);
                        if (registrante != null) {
                            pago.setRegistradoPorUsuario(registrante);
                            pago.setRegistradoPorNombre(registrante.getNombre() + " " + registrante.getApellido());
                            pago.setRegistradoPorCargo(obtenerCargoActual(solicitanteId));
                        }
                    }
                    pagoRepository.save(pago);

                    cuota.setEstado(EstadoCuota.PAGADO);
                    cuota.setMontoPagado(cuota.getMontoCalculado());
                    cuotaRepository.save(cuota);
                    pagos.add(pago);
                }

                Usuario admin = usuarioRepository.findById(solicitanteId).orElse(pagador);
                pagos.forEach(p -> boletasService.generarBoleta(p, admin));

                return new MensajeResponse(
                    "Pago aprobado por S/ " + total.setScale(2, RoundingMode.HALF_UP) +
                    ". Se generaron " + cuotas.size() + " recibo(s) a nombre de " +
                    pagador.getNombre() + " " + pagador.getApellido(), true);

            } else if ("in_process".equals(status) || "pending".equals(status)) {
                return new MensajeResponse("El pago está en proceso. Te notificaremos cuando se confirme.", true);
            } else {
                throw new RuntimeException("Pago rechazado por la entidad emisora. Verifica los datos de tu tarjeta.");
            }
        } catch (HttpClientErrorException e) {
            System.err.println("[MP-MULTIPLE ERROR] HTTP " + e.getStatusCode() + " | " + e.getResponseBodyAsString());
            throw new RuntimeException("Error MP: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("rechazado")) throw e;
            throw new RuntimeException("Error al procesar el pago: " + e.getMessage());
        }
    }

    // ── Utilitario ───────────────────────────────────────────────────
    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    private String obtenerCargoActual(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId).stream()
                .filter(ur -> ur.getRol().getEsDirectivo())
                .map(ur -> ur.getRol().getNombre())
                .findFirst().orElse(null);
    }
}
