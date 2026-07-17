package pe.torreblanca.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FondoProyectoResponse {
    private Integer id;
    private String nombre;
    private String descripcion;
    private BigDecimal metaMonto;
    private String estado;
    private LocalDate fechaInicio;
    private LocalDate fechaCierre;
    private String creadoPorNombre;
    private BigDecimal totalIngresado;
    private BigDecimal totalRetirado;
    private BigDecimal saldo;
    // true si ya tiene al menos un movimiento (ingreso o retiro) registrado
    // — si es false, el proyecto se puede eliminar (fue creado por error o de prueba)
    private boolean tieneMovimientos;
}
