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
}
