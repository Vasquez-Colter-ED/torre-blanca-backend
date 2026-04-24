package pe.torreblanca.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "roles")
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String nombre;
    private String descripcion;

    @Column(name = "es_directivo")
    private Boolean esDirectivo = false;

    private Boolean estado = true;
}
