package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "configuracion_mantenimiento")
public class ConfiguracionMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer mes;
    private Integer anio;

    @Column(name = "costo_por_m2")
    private BigDecimal costoPorM2;

    @Column(name = "total_mensual")
    private BigDecimal totalMensual;

    // COSTO_M2 (clásico) o PORCENTAJE (alicuota)
    @Column(name = "tipo_calculo")
    private String tipoCalculo = "COSTO_M2";

    @Column(name = "total_gastos_estimados")
    private BigDecimal totalGastosEstimados;

    private String observaciones;

    @Column(name = "creado_por")
    private Integer creadoPor;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
