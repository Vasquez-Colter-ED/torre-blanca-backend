package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class GastoRequest {
    private Integer categoriaId;
    private String descripcion;
    private BigDecimal monto;
    private String fechaGasto; // formato: yyyy-MM-dd
    private String comprobanteUrl;
}
