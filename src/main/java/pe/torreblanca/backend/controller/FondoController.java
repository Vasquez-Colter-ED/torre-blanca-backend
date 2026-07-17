package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.FondoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/fondo")
@CrossOrigin(origins = "*")
public class FondoController {

    @Autowired private FondoService fondoService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;

    private Integer getSolicitanteId(String authHeader) {
        String email = jwtUtil.getEmailFromToken(authHeader.substring(7));
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    @GetMapping("/resumen")
    public ResponseEntity<?> resumen() {
        try { return ResponseEntity.ok(fondoService.resumenGeneral()); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/proyectos")
    public ResponseEntity<?> listarProyectos() {
        try { return ResponseEntity.ok(fondoService.listarProyectos()); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/proyectos")
    public ResponseEntity<?> crearProyecto(@RequestBody FondoProyectoRequest request,
                                            @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(fondoService.crearProyecto(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PatchMapping("/proyectos/{id}/estado")
    public ResponseEntity<?> cambiarEstado(@PathVariable Integer id, @RequestParam String estado,
                                            @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(fondoService.cambiarEstadoProyecto(id, estado, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/proyectos/{id}")
    public ResponseEntity<?> eliminarProyecto(@PathVariable Integer id,
                                               @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(fondoService.eliminarProyecto(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/movimientos")
    public ResponseEntity<?> listarMovimientos(@RequestParam(required = false) Integer proyectoId) {
        try {
            List<FondoMovimientoResponse> lista = proyectoId != null
                    ? fondoService.listarMovimientosDeProyecto(proyectoId)
                    : fondoService.listarMovimientosGenerales();
            return ResponseEntity.ok(lista);
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping("/movimientos")
    public ResponseEntity<?> registrarMovimiento(@RequestBody FondoMovimientoRequest request,
                                                  @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(fondoService.registrarMovimiento(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @DeleteMapping("/movimientos/{id}")
    public ResponseEntity<?> eliminarMovimiento(@PathVariable Integer id,
                                                 @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(fondoService.eliminarMovimiento(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}
