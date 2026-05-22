package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class GastoResponse {
    private Integer id;
    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private String fechaGasto;
    private Integer mes;
    private Integer anio;
    private String comprobanteUrl;
    private String registradoPorNombre;
}
