package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.repository.UsuarioRolRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.PagosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pagos")
@CrossOrigin(origins = "*")
public class PagosController {

    @Autowired private PagosService pagosService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;

    private Integer getSolicitanteId(String authHeader) {
        String email = jwtUtil.getEmailFromToken(authHeader.substring(7));
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    private boolean esDirectivo(Integer usuarioId) {
        return usuarioRolRepository.findRolesActivosByUsuarioId(usuarioId)
                .stream().anyMatch(ur -> ur.getRol().getEsDirectivo());
    }

    // ── Configuración mensual ─────────────────────────────────────────

    @GetMapping("/configuraciones")
    public ResponseEntity<?> listarConfiguraciones() {
        return ResponseEntity.ok(pagosService.listarConfiguraciones());
    }

    @PostMapping("/configurar-mes")
    public ResponseEntity<?> configurarMes(@RequestBody ConfigurarMesRequest request,
                                           @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.configurarMesYGenerarCuotas(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // ── Resumen del mes ───────────────────────────────────────────────

    @GetMapping("/resumen/{anio}/{mes}")
    public ResponseEntity<?> resumenMes(@PathVariable Integer anio,
                                        @PathVariable Integer mes) {
        try { return ResponseEntity.ok(pagosService.obtenerResumenMes(mes, anio)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // ── Mis cuotas (residente) ────────────────────────────────────────

    @GetMapping("/mis-cuotas")
    public ResponseEntity<?> misCuotas(@RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.obtenerMisCuotas(getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // ── Registrar pago ────────────────────────────────────────────────

    @PostMapping("/registrar")
    public ResponseEntity<?> registrarPago(@RequestBody RegistrarPagoRequest request,
                                           @RequestHeader("Authorization") String auth) {
        try {
            Integer id = getSolicitanteId(auth);
            return ResponseEntity.ok(pagosService.registrarPago(request, id, esDirectivo(id)));
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // ── Pagos pendientes de verificación ─────────────────────────────

    @GetMapping("/pendientes")
    public ResponseEntity<?> pendientes() {
        return ResponseEntity.ok(pagosService.obtenerPendientesVerificacion());
    }

    // ── Verificar pago ────────────────────────────────────────────────

    @PatchMapping("/{pagoId}/verificar")
    public ResponseEntity<?> verificarPago(@PathVariable Integer pagoId,
                                           @RequestBody VerificarPagoRequest request,
                                           @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.verificarPago(pagoId, request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}
