package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FondoMovimientoResponse {
    private Integer id;
    private Integer proyectoId;
    private String proyectoNombre;
    private String tipo;
    private BigDecimal monto;
    private String concepto;
    private LocalDate fecha;
    private String comprobanteUrl;
    private Integer gastoId;
    private String registradoPorNombre;
}
