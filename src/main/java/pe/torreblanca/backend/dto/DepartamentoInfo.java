package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class DepartamentoInfo {
    private Integer departamentoId;
    private String numero;
    private Integer piso;
    private String tipo; // "PROPIETARIO" o "INQUILINO"
}
