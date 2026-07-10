package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "fondo_movimientos")
public class FondoMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proyecto_id")
    private FondoProyecto proyecto; // null = movimiento general del fondo

    private String tipo; // INGRESO / RETIRO
    private BigDecimal monto;
    private String concepto;
    private LocalDate fecha;

    @Column(name = "comprobante_url")
    private String comprobanteUrl;

    // Si el movimiento es un RETIRO, aquí queda el id del Gasto que se
    // creó automáticamente en el módulo de Gastos para ese mismo retiro.
    @Column(name = "gasto_id")
    private Integer gastoId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
