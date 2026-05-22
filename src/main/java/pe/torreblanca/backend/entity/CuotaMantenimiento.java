package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "cuotas_mantenimiento")
public class CuotaMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "departamento_id")
    private Departamento departamento;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "configuracion_id")
    private ConfiguracionMantenimiento configuracion;

    @Column(name = "monto_calculado")
    private BigDecimal montoCalculado;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responsable_pago_id")
    private Usuario responsablePago;

    @Enumerated(EnumType.STRING)
    private EstadoCuota estado = EstadoCuota.PENDIENTE;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
}
