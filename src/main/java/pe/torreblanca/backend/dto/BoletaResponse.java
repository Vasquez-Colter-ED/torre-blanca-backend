package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BoletaResponse {
    private Integer id;
    private String numeroBoleta;
    private LocalDateTime fechaEmision;
    // Si este pago fue parte de un pago múltiple, los demás meses cubiertos
    // por el mismo comprobante
    private List<String> pagadoJuntoCon;
    // Datos del pago
    private String pagadorNombre;
    private String numeroDepartamento;
    private Integer piso;
    private BigDecimal monto;
    private BigDecimal comision;
    private String metodoPago;
    private String numeroOperacion;
    private LocalDateTime fechaPago;
    // Datos del mes
    private Integer mes;
    private Integer anio;
    // Emitida por
    private String emitidaPorNombre;
    private String voucherUrl;
}
