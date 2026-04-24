package pe.torreblanca.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MensajeResponse {
    private String mensaje;
    private boolean exito;
}
