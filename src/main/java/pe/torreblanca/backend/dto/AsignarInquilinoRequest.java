package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class AsignarInquilinoRequest {
    private Integer usuarioId;
    private Integer departamentoId;
    private Integer propietarioId;
}
