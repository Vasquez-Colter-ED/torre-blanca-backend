package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.Usuario;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.security.JwtUtil;
import pe.torreblanca.backend.service.AuthService;
import pe.torreblanca.backend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try { return ResponseEntity.ok(authService.login(request)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(@RequestHeader("Authorization") String authHeader) {
        try {
            String email = jwtUtil.getEmailFromToken(authHeader.substring(7));
            Usuario usuario = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            return ResponseEntity.ok(usuarioService.obtenerPorId(usuario.getId()));
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // ── Recuperación de contraseña — 3 pasos ─────────────────────────

    // Paso 1 — enviar código al correo
    @PostMapping("/recuperar-password")
    public ResponseEntity<?> recuperarPassword(@RequestBody RecuperarPasswordRequest request) {
        try { return ResponseEntity.ok(authService.recuperarPassword(request)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Paso 2 — verificar código de 6 dígitos
    @PostMapping("/verificar-codigo")
    public ResponseEntity<?> verificarCodigo(@RequestBody VerificarCodigoRequest request) {
        try { return ResponseEntity.ok(authService.verificarCodigo(request)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    // Paso 3 — establecer nueva contraseña
    @PostMapping("/nueva-password")
    public ResponseEntity<?> nuevaPassword(@RequestBody NuevaPasswordRequest request) {
        try { return ResponseEntity.ok(authService.nuevaPassword(request)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}
