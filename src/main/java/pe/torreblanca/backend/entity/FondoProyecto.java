package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fondo_proyectos")
public class FondoProyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;
    private String descripcion;

    @Column(name = "meta_monto")
    private BigDecimal metaMonto;

    private String estado; // ACTIVO, CERRADO, CANCELADO

    @Column(name = "fecha_inicio")
    private LocalDate fechaInicio;

    @Column(name = "fecha_cierre")
    private LocalDate fechaCierre;

    @Column(name = "creado_por")
    private Integer creadoPor;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (estado == null) estado = "ACTIVO";
    }
}
