package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.LoginRequest;
import pe.torreblanca.backend.dto.LoginResponse;
import pe.torreblanca.backend.entity.EstadoUsuario;
import pe.torreblanca.backend.entity.Usuario;
import pe.torreblanca.backend.entity.UsuarioRol;
import pe.torreblanca.backend.repository.UsuarioRepository;
import pe.torreblanca.backend.repository.UsuarioRolRepository;
import pe.torreblanca.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;

    // ── Rate limiter ──────────────────────────────────────────────────
    // Guarda en memoria cuántos intentos fallidos ha tenido cada email
    // y cuándo fue el último. Simple, sin dependencias externas.
    // Regla: máximo 5 intentos fallidos en una ventana de 10 minutos.
    // Al pasar ese límite, se bloquea el intento aunque la contraseña
    // sea correcta, hasta que pasen los 10 minutos.
    private static final int  MAX_INTENTOS    = 5;
    private static final long VENTANA_MS      = 10 * 60 * 1000L; // 10 minutos

    private record IntentosFallidos(int cantidad, Instant desde) {}
    private final Map<String, IntentosFallidos> intentos = new ConcurrentHashMap<>();

    private void verificarRateLimit(String email) {
        IntentosFallidos actual = intentos.get(email);
        if (actual == null) return;

        long transcurrido = Instant.now().toEpochMilli() - actual.desde().toEpochMilli();
        if (transcurrido > VENTANA_MS) {
            intentos.remove(email); return; // ventana expiró, limpia el contador
        }
        if (actual.cantidad() >= MAX_INTENTOS) {
            long restanteMin = (VENTANA_MS - transcurrido) / 60000;
            throw new RuntimeException(
                "Demasiados intentos fallidos. Espera " + (restanteMin + 1) + " minuto(s) antes de intentar de nuevo.");
        }
    }

    private void registrarIntentoFallido(String email) {
        intentos.compute(email, (k, actual) -> {
            if (actual == null || Instant.now().toEpochMilli() - actual.desde().toEpochMilli() > VENTANA_MS)
                return new IntentosFallidos(1, Instant.now());
            return new IntentosFallidos(actual.cantidad() + 1, actual.desde());
        });
    }

    private void limpiarIntentos(String email) {
        intentos.remove(email);
    }

    // ── Login ─────────────────────────────────────────────────────────
    public LoginResponse login(LoginRequest request) {

        // 1. Rate limiter — antes de tocar la BD
        verificarRateLimit(request.getEmail());

        // 2. Buscar usuario
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    registrarIntentoFallido(request.getEmail());
                    return new RuntimeException("Credenciales incorrectas");
                });

        // 3. Verificar estado
        if (usuario.getEstado() != EstadoUsuario.ACTIVO)
            throw new RuntimeException("Tu cuenta está inactiva o suspendida");

        // 4. Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            registrarIntentoFallido(request.getEmail());
            throw new RuntimeException("Credenciales incorrectas");
        }

        // 5. Login exitoso — limpiar intentos fallidos
        limpiarIntentos(request.getEmail());

        // 6. Generar token con jti único de sesión
        String token = jwtUtil.generateToken(usuario.getEmail());
        String jti   = jwtUtil.getJtiFromToken(token);

        // 7. Guardar jti en BD — invalida cualquier sesión anterior
        //    (1 sesión activa por cuenta)
        usuario.setSessionToken(jti);
        usuarioRepository.save(usuario);

        // 8. Rol principal
        List<UsuarioRol> roles = usuarioRolRepository.findRolesActivosByUsuarioId(usuario.getId());
        String rolPrincipal = roles.stream()
                .filter(ur -> ur.getRol().getEsDirectivo())
                .map(ur -> ur.getRol().getNombre())
                .findFirst()
                .orElse(roles.isEmpty() ? "SIN_ROL" : roles.get(0).getRol().getNombre());

        return new LoginResponse(token, usuario.getId(), usuario.getNombre(),
                usuario.getApellido(), usuario.getEmail(), rolPrincipal);
    }
}
