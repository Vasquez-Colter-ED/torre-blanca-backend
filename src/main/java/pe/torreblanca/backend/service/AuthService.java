package pe.torreblanca.backend.service;

import pe.torreblanca.backend.dto.*;
import pe.torreblanca.backend.entity.*;
import pe.torreblanca.backend.repository.*;
import pe.torreblanca.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private UsuarioRolRepository usuarioRolRepository;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;

    // ── Rate limiter ──────────────────────────────────────────────────
    private static final int  MAX_INTENTOS = 5;
    private static final long VENTANA_MS   = 10 * 60 * 1000L;

    private record IntentosFallidos(int cantidad, Instant desde) {}
    private final Map<String, IntentosFallidos> intentos = new ConcurrentHashMap<>();

    private void verificarRateLimit(String email) {
        IntentosFallidos actual = intentos.get(email);
        if (actual == null) return;
        long transcurrido = Instant.now().toEpochMilli() - actual.desde().toEpochMilli();
        if (transcurrido > VENTANA_MS) { intentos.remove(email); return; }
        if (actual.cantidad() >= MAX_INTENTOS) {
            long restanteMin = (VENTANA_MS - transcurrido) / 60000;
            throw new RuntimeException("Demasiados intentos fallidos. Espera " + (restanteMin + 1) + " minuto(s) antes de intentar de nuevo.");
        }
    }

    private void registrarIntentoFallido(String email) {
        intentos.compute(email, (k, actual) -> {
            if (actual == null || Instant.now().toEpochMilli() - actual.desde().toEpochMilli() > VENTANA_MS)
                return new IntentosFallidos(1, Instant.now());
            return new IntentosFallidos(actual.cantidad() + 1, actual.desde());
        });
    }

    private void limpiarIntentos(String email) { intentos.remove(email); }

    // ── Login ─────────────────────────────────────────────────────────
    public LoginResponse login(LoginRequest request) {
        verificarRateLimit(request.getEmail());

        // Si tiene exactamente 8 dígitos es un DNI, si no es email
        Usuario usuario;
        if (request.getEmail().matches("\\d{8}")) {
            usuario = usuarioRepository.findByDni(request.getEmail())
                    .orElseThrow(() -> {
                        registrarIntentoFallido(request.getEmail());
                        return new RuntimeException("Credenciales incorrectas");
                    });
        } else {
            usuario = usuarioRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> {
                        registrarIntentoFallido(request.getEmail());
                        return new RuntimeException("Credenciales incorrectas");
                    });
        }

        if (usuario.getEstado() != EstadoUsuario.ACTIVO)
            throw new RuntimeException("Tu cuenta está inactiva o suspendida");

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            registrarIntentoFallido(request.getEmail());
            throw new RuntimeException("Credenciales incorrectas");
        }

        limpiarIntentos(request.getEmail());

        String token = jwtUtil.generateToken(usuario.getEmail());
        String jti   = jwtUtil.getJtiFromToken(token);
        usuario.setSessionToken(jti);
        usuarioRepository.save(usuario);

        List<UsuarioRol> roles = usuarioRolRepository.findRolesActivosByUsuarioId(usuario.getId());
        String rolPrincipal = roles.stream()
                .filter(ur -> ur.getRol().getEsDirectivo())
                .map(ur -> ur.getRol().getNombre())
                .findFirst()
                .orElse(roles.isEmpty() ? "SIN_ROL" : roles.get(0).getRol().getNombre());

        return new LoginResponse(token, usuario.getId(), usuario.getNombre(),
                usuario.getApellido(), usuario.getEmail(), rolPrincipal);
    }

    // ── Recuperación de contraseña ────────────────────────────────────

    // Paso 1: genera código de 6 dígitos, lo guarda en BD y envía el email
    public MensajeResponse recuperarPassword(RecuperarPasswordRequest request) {
        System.out.println("[RECUPERACION] Solicitud recibida para: " + request.getEmail());
        var usuarioOpt = usuarioRepository.findByEmail(request.getEmail());
        System.out.println("[RECUPERACION] Usuario encontrado: " + usuarioOpt.isPresent());
        if (usuarioOpt.isPresent()) {
            System.out.println("[RECUPERACION] Estado del usuario: " + usuarioOpt.get().getEstado());
        }

        usuarioOpt.ifPresent(usuario -> {
            if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
                System.out.println("[RECUPERACION] Usuario inactivo, no se envia codigo");
                return;
            }

            String codigo = String.format("%06d", new Random().nextInt(999999));

            // Primero guardamos el código en BD — esto no debe fallar
            usuario.setResetCode(codigo);
            usuario.setResetCodeExpires(LocalDateTime.now().plusMinutes(15));
            usuario.setResetCodeVerificado(false);
            usuarioRepository.save(usuario);

            // Log siempre visible en Render
            System.out.println("[RECUPERACION] Usuario: " + usuario.getEmail() + " | Codigo: " + codigo);

            // Intentar enviar email — si falla, el código sigue guardado en BD
            try {
                emailService.enviarCodigoRecuperacion(usuario.getEmail(), usuario.getNombre(), codigo);
                System.out.println("[RECUPERACION] Email enviado correctamente a: " + usuario.getEmail());
            } catch (Exception e) {
                System.err.println("[RECUPERACION] Fallo email a " + usuario.getEmail() + ": " + e.getMessage());
                // No relanzamos — el código ya está en BD y el usuario puede avanzar
            }
        });

        return new MensajeResponse("Si ese correo está registrado, recibirás un código en breve.",
                usuarioOpt.isPresent());
    }

    // Paso 2: verifica que el código sea correcto y no haya expirado
    public MensajeResponse verificarCodigo(VerificarCodigoRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Código inválido o expirado"));

        if (usuario.getResetCode() == null
                || !usuario.getResetCode().equals(request.getCodigo())) {
            throw new RuntimeException("El código ingresado no es correcto");
        }

        if (LocalDateTime.now().isAfter(usuario.getResetCodeExpires())) {
            throw new RuntimeException("El código expiró. Solicita uno nuevo.");
        }

        // Marca como verificado para que el paso 3 pueda proceder
        usuario.setResetCodeVerificado(true);
        usuarioRepository.save(usuario);

        return new MensajeResponse("Código verificado correctamente", true);
    }

    // Paso 3: establece la nueva contraseña (solo si el código fue verificado)
    public MensajeResponse nuevaPassword(NuevaPasswordRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Solicitud inválida"));

        if (usuario.getResetCode() == null
                || !Boolean.TRUE.equals(usuario.getResetCodeVerificado())) {
            throw new RuntimeException("Debes verificar el código primero");
        }

        if (LocalDateTime.now().isAfter(usuario.getResetCodeExpires())) {
            throw new RuntimeException("El código expiró. Solicita uno nuevo.");
        }

        // Establece la nueva contraseña
        usuario.setPasswordHash(passwordEncoder.encode(request.getNuevaPassword()));

        // Limpia el código para que no pueda reutilizarse
        usuario.setResetCode(null);
        usuario.setResetCodeExpires(null);
        usuario.setResetCodeVerificado(false);

        // Invalida la sesión activa por seguridad
        usuario.setSessionToken(null);

        usuarioRepository.save(usuario);

        return new MensajeResponse("Contraseña restablecida correctamente. Ya puedes iniciar sesión.", true);
    }
}
