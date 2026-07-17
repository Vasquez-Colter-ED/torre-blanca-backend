package pe.torreblanca.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.torreblanca.backend.entity.Usuario;
import pe.torreblanca.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserDetailsServiceImpl userDetailsService;
    @Autowired private UsuarioRepository usuarioRepository;

    // Límite de sesión por inactividad: si el usuario no hace ninguna
    // petición autenticada en este tiempo, la sesión se cierra aunque el
    // JWT todavía sea válido por sus 24h. Cualquier acción (navegar entre
    // módulos, guardar algo, etc.) cuenta como actividad y reinicia el conteo.
    private static final long LIMITE_INACTIVIDAD_MINUTOS = 30;
    // No se reescribe la marca de actividad en CADA petición (sería una
    // escritura a BD por cada clic) — solo si ya pasó al menos este tiempo
    // desde la última vez que se guardó, sin afectar la precisión del límite
    private static final long UMBRAL_ACTUALIZACION_SEGUNDOS = 60;

    // Rutas públicas que no necesitan token — el filtro las deja pasar sin validar
    private static final String[] RUTAS_PUBLICAS = {
        "/api/auth/login",
        "/api/auth/ping",
        "/api/auth/recuperar-password",
        "/api/auth/verificar-codigo",
        "/api/auth/nueva-password",
        "/api/setup/",
        "/api/usuarios/catalogos/"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Log para debug de 403s
        if (path.contains("departamentos")) {
            System.out.println("[SECURITY] " + method + " " + path +
                " | Auth header: " + (request.getHeader("Authorization") != null ? "presente" : "AUSENTE"));
        }

        // Si es ruta pública, deja pasar sin validar token
        for (String ruta : RUTAS_PUBLICAS) {
            if (path.startsWith(ruta)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        // GET /api/departamentos (exacto, sin subrutas) también es público
        if ("GET".equals(method) && "/api/departamentos".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtUtil.getEmailFromToken(token);
        String jti   = jwtUtil.getJtiFromToken(token);

        // Validación de sesión única
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        if (usuarioOpt.isEmpty() || !jti.equals(usuarioOpt.get().getSessionToken())) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Sesión inválida. Por favor inicia sesión nuevamente.");
            return;
        }

        Usuario usuario = usuarioOpt.get();
        LocalDateTime ahora = LocalDateTime.now();

        // Límite de sesión por inactividad
        if (usuario.getUltimaActividad() != null &&
                Duration.between(usuario.getUltimaActividad(), ahora).toMinutes() > LIMITE_INACTIVIDAD_MINUTOS) {
            usuario.setSessionToken(null);
            usuarioRepository.save(usuario);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Tu sesión expiró por inactividad. Inicia sesión nuevamente.");
            return;
        }

        // Esta petición cuenta como actividad — reinicia el contador (con
        // el pequeño umbral de arriba para no escribir en la BD de más)
        if (usuario.getUltimaActividad() == null ||
                Duration.between(usuario.getUltimaActividad(), ahora).toSeconds() >= UMBRAL_ACTUALIZACION_SEGUNDOS) {
            usuario.setUltimaActividad(ahora);
            usuarioRepository.save(usuario);
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
