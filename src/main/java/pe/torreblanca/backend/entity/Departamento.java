package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "departamentos")
public class Departamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String numero;
    private Integer piso;

    @Column(name = "metros_cuadrados")
    private BigDecimal metrosCuadrados;

    @Column(name = "porcentaje", precision = 5, scale = 2)
    private BigDecimal porcentaje;

    private String tipo; // DEPARTAMENTO o ESTACIONAMIENTO

    private String descripcion;

    @Enumerated(EnumType.STRING)
    private EstadoDepartamento estado = EstadoDepartamento.OCUPADO;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
