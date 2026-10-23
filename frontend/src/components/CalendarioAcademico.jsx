import { useEffect, useState } from "react";
import { obtenerCalendario } from "../services/calendarioService";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Tabla from "./ui/Tabla";
import Tarjeta from "./ui/Tarjeta";

// Las columnas cortas no se parten: en pantallas pequeñas la tabla se desplaza en horizontal.
// Tabla aplica className solo a las celdas, por eso los títulos de dos palabras llevan su propio span.
const COLUMNAS = [
  { titulo: "Materia", className: "min-w-40 font-semibold" },
  { titulo: "Código", className: "whitespace-nowrap" },
  { titulo: "Día", className: "whitespace-nowrap" },
  { titulo: <span className="whitespace-nowrap">Hora Inicio</span>, className: "whitespace-nowrap font-mono" },
  { titulo: <span className="whitespace-nowrap">Hora Fin</span>, className: "whitespace-nowrap font-mono" },
  { titulo: "Modalidad", className: "whitespace-nowrap" },
  { titulo: "Docente", className: "min-w-40" },
];

function CalendarioAcademico({ estudiante }) {

  const [horarios, setHorarios] = useState([]);

  useEffect(() => {

    cargarCalendario();

  }, []);

  const cargarCalendario = async () => {

    try {

      const data =
        await obtenerCalendario(estudiante.id);

      setHorarios(data);

    } catch (error) {

      console.error(error);

    }

  };

  const filas = horarios.map((h) => ({
    celdas: [
      h.materia,
      h.codigo,
      h.diaSemana,
      h.horaInicio,
      h.horaFin,
      h.modalidad,
      h.docenteTutor,
    ],
  }));

  return (

    <section className="space-y-5">

      <EncabezadoModulo
        descripcion="Horarios de clase de tus materias inscritas."
      />

      <Tarjeta>
        <Tabla
          columnas={COLUMNAS}
          filas={filas}
          mensajeVacio="No existen horarios registrados."
        />
      </Tarjeta>

    </section>

  );
}

export default CalendarioAcademico;