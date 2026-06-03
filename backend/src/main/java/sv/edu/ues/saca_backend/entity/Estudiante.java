package sv.edu.ues.saca_backend.entity;

import jakarta.persistence.*;
import java.sql.Timestamp;
@Entity
@Table(name = "estudiantes")
public class Estudiante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String carnet;

    private String nombre;
    private Integer intentosFallidos;

private Timestamp bloqueadoHasta;

    @Column(name = "correo_institucional")
    private String correoInstitucional;

    private String contrasenia;

    @Column(name = "cuenta_activa")
    private Boolean cuentaActiva;

    public Estudiante() {}

    public Long getId() {
        return id;
    }

    public String getCarnet() {
        return carnet;
    }

    public void setCarnet(String carnet) {
        this.carnet = carnet;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreoInstitucional() {
        return correoInstitucional;
    }

    public void setCorreoInstitucional(String correoInstitucional) {
        this.correoInstitucional = correoInstitucional;
    }

    public String getContrasenia() {
        return contrasenia;
    }

    public void setContrasenia(String contrasenia) {
        this.contrasenia = contrasenia;
    }

    public Boolean getCuentaActiva() {
        return cuentaActiva;
    }

    public void setCuentaActiva(Boolean cuentaActiva) {
        this.cuentaActiva = cuentaActiva;
    }
    public Integer getIntentosFallidos() {
    return intentosFallidos;
}

public void setIntentosFallidos(Integer intentosFallidos) {
    this.intentosFallidos = intentosFallidos;
}

public Timestamp getBloqueadoHasta() {
    return bloqueadoHasta;
}

public void setBloqueadoHasta(Timestamp bloqueadoHasta) {
    this.bloqueadoHasta = bloqueadoHasta;
}
}