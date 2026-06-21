package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;
    private String apellido;
    private String dni;
    private String email;
    private String telefono;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Enumerated(EnumType.STRING)
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    // Sesión única — se actualiza en cada login
    @Column(name = "session_token")
    private String sessionToken;

    // Recuperación de contraseña
    @Column(name = "reset_code")
    private String resetCode;

    @Column(name = "reset_code_expires")
    private LocalDateTime resetCodeExpires;

    @Column(name = "reset_code_verificado")
    private Boolean resetCodeVerificado = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private List<UsuarioRol> usuarioRoles;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
