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

    // Extrae el ID del admin desde el token JWT del header
    private Integer getAdminId(String authHeader) {
        String token = authHeader.substring(7);
        String email = jwtUtil.getEmailFromToken(token);
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Admin no encontrado"))
                .getId();
    }

    // GET /api/usuarios - Listar todos
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarTodos() {
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    // GET /api/usuarios/{id} - Obtener uno
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(usuarioService.obtenerPorId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /api/usuarios - Crear usuario
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody CrearUsuarioRequest request,
                                   @RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(usuarioService.crear(request, getAdminId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PUT /api/usuarios/{id} - Editar usuario
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(@PathVariable Integer id,
                                    @RequestBody EditarUsuarioRequest request,
                                    @RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(usuarioService.editar(id, request, getAdminId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PATCH /api/usuarios/{id}/desactivar - Soft delete
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable Integer id,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(usuarioService.desactivar(id, getAdminId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // PATCH /api/usuarios/{id}/reactivar - Reactivar
    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<?> reactivar(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(usuarioService.reactivar(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /api/usuarios/{id}/roles - Asignar rol
    @PostMapping("/{id}/roles")
    public ResponseEntity<?> asignarRol(@PathVariable Integer id,
                                        @RequestBody AsignarRolRequest request,
                                        @RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(usuarioService.asignarRol(id, request.getRolId(), getAdminId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // DELETE /api/usuarios/{id}/roles/{rolId} - Revocar rol
    @DeleteMapping("/{id}/roles/{rolId}")
    public ResponseEntity<?> revocarRol(@PathVariable Integer id, @PathVariable Integer rolId) {
        try {
            return ResponseEntity.ok(usuarioService.revocarRol(id, rolId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /api/usuarios/{id}/permisos - Asignar permiso personalizado
    @PostMapping("/{id}/permisos")
    public ResponseEntity<?> asignarPermiso(@PathVariable Integer id,
                                            @RequestBody AsignarPermisoRequest request,
                                            @RequestHeader("Authorization") String authHeader) {
        try {
            return ResponseEntity.ok(usuarioService.asignarPermiso(id, request, getAdminId(authHeader)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /api/usuarios/catalogos/roles - Lista de roles disponibles
    @GetMapping("/catalogos/roles")
    public ResponseEntity<List<Rol>> listarRoles() {
        return ResponseEntity.ok(usuarioService.listarRoles());
    }

    // GET /api/usuarios/catalogos/modulos - Lista de módulos
    @GetMapping("/catalogos/modulos")
    public ResponseEntity<List<Modulo>> listarModulos() {
        return ResponseEntity.ok(usuarioService.listarModulos());
    }

    // GET /api/usuarios/catalogos/permisos - Lista de permisos
    @GetMapping("/catalogos/permisos")
    public ResponseEntity<List<Permiso>> listarPermisos() {
        return ResponseEntity.ok(usuarioService.listarPermisos());
    }
}
