package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "boletas")
public class Boleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pago_id")
    private PagoMantenimiento pago;

    @Column(name = "numero_boleta", unique = true)
    private String numeroBoleta;

    @Column(name = "fecha_emision")
    private LocalDateTime fechaEmision;

    @Column(name = "url_pdf")
    private String urlPdf;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "emitida_por")
    private Usuario emitidaPor;
}
