package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PagoDetalleResponse {
    private Integer pagoId;
    private String pagadorNombre;
    private BigDecimal monto;
    private String metodoPago;
    private String numeroOperacion;
    private String voucherUrl;
    private String estado;
    private String observaciones;
    private LocalDateTime fechaPago;
    private LocalDateTime fechaVerificacion;
    private String verificadoPorNombre;
}
