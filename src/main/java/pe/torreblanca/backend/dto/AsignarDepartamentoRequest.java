package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class AsignarDepartamentoRequest {
    private Integer usuarioId;
    private Integer departamentoId;
}
