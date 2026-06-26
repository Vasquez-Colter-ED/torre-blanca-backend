package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class EditarUsuarioRequest {
    private String nombre;
    private String apellido;
    private String dni;
    private String telefono;
    private String email;
    private String nuevaPassword; // solo directivos pueden cambiar contraseña ajena
    private Integer rolId;         // solo directivos pueden cambiar rol
    private Integer departamentoId; // solo directivos
    private String tipoResidencia; // PROPIETARIO o INQUILINO
    private Integer propietarioId;  // requerido si tipoResidencia=INQUILINO
}
