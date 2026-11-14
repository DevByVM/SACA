package com.saca.api;

import com.saca.api.entity.TipoActividad;
import com.saca.api.entity.TipoActividadAcademica;

public final class TipoActividadPrueba {

    private TipoActividadPrueba() {
    }

    public static TipoActividad crear(TipoActividadAcademica tipo) {
        return switch (tipo) {
            case PARCIAL -> new TipoActividad(1, "Parcial", true);
            case LABORATORIO -> new TipoActividad(2, "Laboratorio", true);
            case PROYECTO -> new TipoActividad(3, "Proyecto", true);
            case INVESTIGACION -> new TipoActividad(4, "Investigación", true);
        };
    }
}
