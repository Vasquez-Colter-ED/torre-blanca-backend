package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RegistrarPagoRequest {
    private Integer cuotaId;
    private BigDecimal monto;
    private String metodoPago;
    private String numeroOperacion;
    private String voucherUrl;
    private String observaciones;
}
