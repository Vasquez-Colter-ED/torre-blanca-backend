package pe.torreblanca.backend.controller;

import pe.torreblanca.backend.dto.LoginRequest;
import pe.torreblanca.backend.dto.LoginResponse;
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
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Devuelve el perfil completo del usuario logueado (con permisos extra)
    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = authHeader.substring(7);
            String email = jwtUtil.getEmailFromToken(token);
            Usuario usuario = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
            return ResponseEntity.ok(usuarioService.obtenerPorId(usuario.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
