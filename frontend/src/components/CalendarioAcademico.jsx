import { useEffect, useState } from "react";
import { obtenerCalendario } from "../services/calendarioService";

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

  return (

    <div className="bg-white p-6 rounded-xl shadow">

      <h2 className="text-2xl font-bold mb-6">
        Calendario Académico Semanal
      </h2>

      {horarios.length === 0 ? (

        <p>No existen horarios registrados.</p>

      ) : (

        <div className="overflow-x-auto">

          <table className="w-full border">

            <thead>

              <tr className="bg-[#960000] text-white">

                <th className="p-3">Materia</th>
                <th className="p-3">Código</th>
                <th className="p-3">Día</th>
                <th className="p-3">Hora Inicio</th>
                <th className="p-3">Hora Fin</th>
                <th className="p-3">Modalidad</th>
                <th className="p-3">Docente</th>

              </tr>

            </thead>

            <tbody>

              {horarios.map((h, index) => (

                <tr
                  key={index}
                  className="border-b text-center hover:bg-gray-50"
                >

                  <td className="p-3 font-semibold">
                    {h.materia}
                  </td>

                  <td className="p-3">
                    {h.codigo}
                  </td>

                  <td className="p-3">
                    {h.diaSemana}
                  </td>

                  <td className="p-3">
                    {h.horaInicio}
                  </td>

                  <td className="p-3">
                    {h.horaFin}
                  </td>

                  <td className="p-3">
                    {h.modalidad}
                  </td>

                  <td className="p-3">
                    {h.docenteTutor}
                  </td>

                </tr>

              ))}

            </tbody>

          </table>

        </div>

      )}

    </div>

  );
}

export default CalendarioAcademico;