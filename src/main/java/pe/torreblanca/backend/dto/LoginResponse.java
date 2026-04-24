package pe.torreblanca.backend.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String tipo = "Bearer";
    private Integer id;
    private String nombre;
    private String apellido;
    private String email;
    private String rol;

    public LoginResponse(String token, Integer id, String nombre, String apellido, String email, String rol) {
        this.token = token;
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.rol = rol;
    }
}
