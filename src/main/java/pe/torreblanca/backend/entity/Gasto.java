package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "gastos")
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "categoria_id")
    private CategoriaGasto categoria;

    private String descripcion;
    private BigDecimal monto;

    @Column(name = "fecha_gasto")
    private LocalDate fechaGasto;

    private Integer mes;
    private Integer anio;

    @Column(name = "comprobante_url")
    private String comprobanteUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "registrado_por")
    private Usuario registradoPor;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
