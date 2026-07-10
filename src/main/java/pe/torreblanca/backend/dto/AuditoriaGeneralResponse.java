package pe.torreblanca.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditoriaGeneralResponse {
    private Integer id;
    private LocalDateTime fecha;
    private Integer usuarioId;
    private String usuarioNombre;
    private String accion;
    private String tablaAfectada;
    private Integer registroId;
    private String datosAnteriores; // JSON crudo, o null si no aplica
    private String datosNuevos;     // JSON crudo, o null si no aplica
}
