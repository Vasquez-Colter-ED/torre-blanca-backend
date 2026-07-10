package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ReportesAnioResponse {
    private Integer anio;
    private BigDecimal totalRecaudadoAnio;
    private BigDecimal totalGastosAnio;
    private BigDecimal balanceAnio;
    private List<DatoMensual> datosMensuales;

    @Data
    public static class DatoMensual {
        private String mes;
        private BigDecimal recaudado;
        private BigDecimal gastos;
        private BigDecimal balance;
        private Integer pagados;
        private Integer total;
        private BigDecimal recaudadoEfectivo;
        private BigDecimal recaudadoDigital;
    }
}
