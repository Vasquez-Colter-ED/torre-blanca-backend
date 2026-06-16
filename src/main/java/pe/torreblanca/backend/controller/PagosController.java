package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.repository.UsuarioRolRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.PagosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PutMapping("/configuraciones/{id}")
    public ResponseEntity<?> editarConfiguracion(@PathVariable Integer id,
                                                 @RequestBody EditarConfiguracionRequest request,
                                                 @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.editarConfiguracion(id, request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/configuraciones/{id}")
    public ResponseEntity<?> eliminarConfiguracion(@PathVariable Integer id,
                                                   @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.eliminarConfiguracion(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/resumen/{anio}/{mes}")
    public ResponseEntity<?> resumenMes(@PathVariable Integer anio, @PathVariable Integer mes) {
        try { return ResponseEntity.ok(pagosService.obtenerResumenMes(mes, anio)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/mis-cuotas")
    public ResponseEntity<?> misCuotas(@RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.obtenerMisCuotas(getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Lista de residentes de un departamento específico — para el selector
    // de "quién realizó el pago" cuando el directivo registra manualmente.
    @GetMapping("/departamento/{id}/residentes")
    public ResponseEntity<?> residentesDeDepto(@PathVariable Integer id) {
        try { return ResponseEntity.ok(pagosService.listarResidentesDeDepto(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrarPago(@RequestBody RegistrarPagoRequest request,
                                           @RequestHeader("Authorization") String auth) {
        try {
            Integer id = getSolicitanteId(auth);
            return ResponseEntity.ok(pagosService.registrarPago(request, id, esDirectivo(id)));
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/pendientes")
    public ResponseEntity<?> pendientes() {
        return ResponseEntity.ok(pagosService.obtenerPendientesVerificacion());
    }

    @PatchMapping("/{pagoId}/verificar")
    public ResponseEntity<?> verificarPago(@PathVariable Integer pagoId,
                                           @RequestBody VerificarPagoRequest request,
                                           @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(pagosService.verificarPago(pagoId, request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}
