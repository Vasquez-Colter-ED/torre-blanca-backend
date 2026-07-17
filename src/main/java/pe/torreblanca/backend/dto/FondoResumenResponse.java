package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class FondoResumenResponse {
    // Saldo real del fondo = todo lo recaudado en mantenimiento (histórico)
    // + ingresos extra registrados directamente al fondo (ej. pollada,
    // donaciones, saldo inicial de apertura) − todos los gastos registrados
    // (incluye los retiros del fondo, que se registran como gasto automático).
    // Representa el efectivo real disponible en la cuenta de la residencial.
    private BigDecimal saldoTotal;

    // Desglose para que el directivo entienda de dónde sale el saldo
    private BigDecimal totalPagosMantenimiento; // histórico, todo lo verificado
    private BigDecimal totalIngresosFondo;      // aportes extra registrados directo al fondo
    private BigDecimal totalGastos;             // histórico, todas las categorías

    // Se mantienen por compatibilidad / vista de movimientos propios del fondo
    private BigDecimal totalIngresado; // = totalIngresosFondo
    private BigDecimal totalRetirado;  // suma de movimientos RETIRO del fondo

    private long proyectosActivos;
}
