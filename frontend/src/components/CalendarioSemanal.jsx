import { useEffect, useState } from "react";
import axios from "axios";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";

function CalendarioSemanal({ estudianteId }) {

  const [horarios, setHorarios] = useState([]);

  useEffect(() => {
    cargar();
  }, [estudianteId]);

  const cargar = async () => {
    try {
      const res = await axios.get(
        `${API_BASE_URL}/calendario/semanal/${estudianteId}`
      );
      setHorarios(res.data);
    } catch (error) {
      console.error("Error cargando calendario", error);
    }
  };

  const dias = [
    "LUNES",
    "MARTES",
    "MIERCOLES",
    "JUEVES",
    "VIERNES",
    "SABADO",
    "DOMINGO"
  ];

  const filtrarPorDia = (dia) =>
    horarios.filter(h => h.diaSemana === dia);

  return (
    <div className="p-6 bg-white rounded-xl shadow">

      <h2 className="text-2xl font-bold mb-6 text-[#430000]">
        Calendario Académico Semanal
      </h2>

      <div className="grid grid-cols-7 gap-2">

        {dias.map((dia) => (
          <div key={dia} className="border rounded-lg p-2 bg-gray-50">

            <h3 className="font-bold text-[#960000] mb-2 text-sm">
              {dia}
            </h3>

            {filtrarPorDia(dia).map((h) => (
              <div
                key={h.id}
                className="bg-white border rounded p-2 mb-2 text-xs"
              >
                <p className="font-semibold">
                  {h.horaInicio} - {h.horaFin}
                </p>

                <p>{h.materiaInscrita?.nombre}</p>

                <p className="text-gray-500">
                  {h.modalidad}
                </p>
              </div>
            ))}

          </div>
        ))}

      </div>
    </div>
  );
}

export default CalendarioSemanal;