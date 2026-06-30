package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pagos_mantenimiento")
public class PagoMantenimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuota_id")
    private CuotaMantenimiento cuota;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pagador_id")
    private Usuario pagador;

    private BigDecimal monto;

    @Column(name = "comision", precision = 10, scale = 2)
    private BigDecimal comision;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_pago")
    private MetodoPago metodoPago;

    @Column(name = "numero_operacion")
    private String numeroOperacion;

    @Column(name = "voucher_url")
    private String voucherUrl;

    @Enumerated(EnumType.STRING)
    private EstadoPago estado = EstadoPago.PENDIENTE_VERIFICACION;

    @Column(name = "registrado_por")
    private String registradoPor = "RESIDENTE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPorUsuario; // solo se llena cuando un directivo registra el pago manualmente

    @Column(name = "registrado_por_nombre")
    private String registradoPorNombre; // snapshot del nombre al momento del registro

    @Column(name = "registrado_por_cargo")
    private String registradoPorCargo; // snapshot del cargo (PRESIDENTE/SECRETARIO/TESORERO) al momento del registro

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verificado_por")
    private Usuario verificadoPor;

    @Column(name = "verificado_por_cargo")
    private String verificadoPorCargo; // snapshot del cargo al momento de verificar

    @Column(name = "fecha_verificacion")
    private LocalDateTime fechaVerificacion;

    private String observaciones;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }
}
