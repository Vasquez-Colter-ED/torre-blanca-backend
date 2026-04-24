package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class CrearUsuarioRequest {
    private String nombre;
    private String apellido;
    private String dni;
    private String email;
    private String telefono;
    private String password;
    private Integer rolId;
}
