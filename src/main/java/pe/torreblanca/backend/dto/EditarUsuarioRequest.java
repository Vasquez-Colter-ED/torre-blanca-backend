package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class EditarUsuarioRequest {
    private String nombre;
    private String apellido;
    private String dni;
    private String telefono;
    private String email;
}
