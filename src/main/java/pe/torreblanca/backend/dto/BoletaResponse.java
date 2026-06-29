package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BoletaResponse {
    private Integer id;
    private String numeroBoleta;
    private LocalDateTime fechaEmision;
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
