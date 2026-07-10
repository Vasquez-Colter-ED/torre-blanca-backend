package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FondoResumenResponse {
    private BigDecimal totalIngresado;
    private BigDecimal totalRetirado;
    private BigDecimal saldoTotal;
    private long proyectosActivos;
}
