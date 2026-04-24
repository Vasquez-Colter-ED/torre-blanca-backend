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

import java.util.List;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private UsuarioRolRepository usuarioRolRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        // Buscar usuario por email
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales incorrectas"));

        // Verificar que esté activo
        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new RuntimeException("Tu cuenta está inactiva o suspendida");
        }

        // Verificar contraseña
        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new RuntimeException("Credenciales incorrectas");
        }

        // Generar token JWT
        String token = jwtUtil.generateToken(usuario.getEmail());

        // Obtener rol principal (directivo primero si tiene uno)
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
