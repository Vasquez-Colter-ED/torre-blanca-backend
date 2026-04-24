package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired private UsuarioService usuarioService;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioRepository usuarioRepository;

    private Integer getSolicitanteId(String authHeader) {
        String token = authHeader.substring(7);
        String email = jwtUtil.getEmailFromToken(token);
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId();
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarTodos() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try { return ResponseEntity.ok(usuarioService.obtenerPorId(id)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody CrearUsuarioRequest request,
                                   @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.crear(request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Integer id,
                                    @RequestBody EditarUsuarioRequest request,
                                    @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.editar(id, request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable Integer id,
                                        @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.desactivar(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<?> reactivar(@PathVariable Integer id,
                                       @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.reactivar(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Asignar rol
    @PostMapping("/{id}/roles")
    public ResponseEntity<?> asignarRol(@PathVariable Integer id,
                                        @RequestBody AsignarRolRequest request,
                                        @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.asignarRol(id, request.getRolId(), getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Revocar rol — usa el ID de la asignación (usuarios_roles.id)
    @DeleteMapping("/{id}/roles/{rolId}")
    public ResponseEntity<?> revocarRol(@PathVariable Integer id,
                                        @PathVariable Integer rolId,
                                        @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.revocarRol(id, rolId, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Asignar permiso extra
    @PostMapping("/{id}/permisos")
    public ResponseEntity<?> asignarPermiso(@PathVariable Integer id,
                                            @RequestBody AsignarPermisoRequest request,
                                            @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.asignarPermiso(id, request, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Revocar permiso extra — usa el ID del registro en usuarios_permisos
    @DeleteMapping("/permisos/{asignacionId}")
    public ResponseEntity<?> revocarPermiso(@PathVariable Integer asignacionId,
                                            @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.revocarPermiso(asignacionId, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Restablecer permisos al default del rol
    @PostMapping("/{id}/permisos/restablecer")
    public ResponseEntity<?> restablecerPermisos(@PathVariable Integer id,
                                                 @RequestHeader("Authorization") String auth) {
        try { return ResponseEntity.ok(usuarioService.restablecerPermisos(id, getSolicitanteId(auth))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/catalogos/roles")
    public ResponseEntity<List<Rol>> listarRoles() { return ResponseEntity.ok(usuarioService.listarRoles()); }

    @GetMapping("/catalogos/modulos")
    public ResponseEntity<List<Modulo>> listarModulos() { return ResponseEntity.ok(usuarioService.listarModulos()); }

    @GetMapping("/catalogos/permisos")
    public ResponseEntity<List<Permiso>> listarPermisos() { return ResponseEntity.ok(usuarioService.listarPermisos()); }
}
