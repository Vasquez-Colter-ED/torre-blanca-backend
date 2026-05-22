package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class InquilinoInfo {
    private Integer asignacionId; // ID en inquilinos_departamentos para poder eliminarlo
    private Integer usuarioId;
    private String nombre;
    private String email;
}
