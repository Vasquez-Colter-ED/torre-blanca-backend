package pe.torreblanca.backend;

import pe.torreblanca.backend.entity.Usuario;
import pe.torreblanca.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${app.init-passwords:false}")
    private boolean initPasswords;

    @Override
    public void run(String... args) {
        if (initPasswords) {
            String pass = passwordEncoder.encode("Torre2024");
            for (Usuario u : usuarioRepository.findAll()) {
                if (!u.getPasswordHash().startsWith("$2a$")) {
                    u.setPasswordHash(pass);
                    usuarioRepository.save(u);
                }
            }
            System.out.println("✅ Contraseñas inicializadas. Password: Torre2024");
        }
    }
}