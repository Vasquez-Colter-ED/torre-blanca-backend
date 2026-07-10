package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FondoMovimientoRequest {
    private Integer proyectoId; // null = movimiento general
    private String tipo;        // INGRESO / RETIRO
    private BigDecimal monto;
    private String concepto;
    private String fecha;       // yyyy-MM-dd
    private String comprobanteUrl;
}
