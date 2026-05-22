package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ReportesMesResponse {
    private Integer mes;
    private Integer anio;
    // Financiero
    private BigDecimal totalRecaudado;
    private BigDecimal totalEsperado;
    private BigDecimal totalGastos;
    private BigDecimal balance;
    private Integer deptosPagados;
    private Integer deptosTotal;
    // Deudores
    private List<DeudorInfo> deudores;
    // Gastos por categoría
    private Map<String, BigDecimal> gastosPorCategoria;
}
