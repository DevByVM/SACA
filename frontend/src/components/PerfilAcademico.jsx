import { useCallback, useEffect, useState } from "react";
import {obtenerPerfil,actualizarPerfil} from "../services/perfilService";

function PerfilAcademico() {

  const [form, setForm] = useState({
    id: "",
    nombre: "",
    carnet: "",
    correoInstitucional: "",
    contrasenia: ""
  });

  const cargarPerfil = useCallback(async () => {

    const data =
      await obtenerPerfil();

    setForm({
      id: data.id,
      nombre: data.nombre,
      carnet: data.carnet,
      correoInstitucional:
        data.correoInstitucional,
      contrasenia: ""
    });
  }, []);

  useEffect(() => {
    cargarPerfil();
  }, [cargarPerfil]);

  const handleChange = (e) => {

    setForm({
      ...form,
      [e.target.name]:
        e.target.value
    });
  };

  const handleSubmit = async (e) => {

    e.preventDefault();

    try {

      const respuesta =
        await actualizarPerfil(
          form.id,
          {
            nombre: form.nombre,
            carnet: form.carnet,
            correoInstitucional:
              form.correoInstitucional,
            contrasenia:
              form.contrasenia
          }
        );

      alert(respuesta);

    } catch {

      alert(
        "Error al actualizar perfil"
      );
    }
  };

  return (

    <div className="bg-white p-6 rounded-xl shadow">

      <h2 className="text-2xl font-bold mb-6">
        Perfil Académico
      </h2>

      <form
        onSubmit={handleSubmit}
        className="space-y-4"
      >

        <input
          type="text"
          name="nombre"
          value={form.nombre}
          onChange={handleChange}
          placeholder="Nombre"
          className="w-full border p-3 rounded"
        />

        <input
          type="text"
          name="carnet"
          value={form.carnet}
          onChange={handleChange}
          placeholder="Carnet"
          className="w-full border p-3 rounded"
        />

        <input
          type="email"
          name="correoInstitucional"
          value={form.correoInstitucional}
          onChange={handleChange}
          placeholder="Correo"
          className="w-full border p-3 rounded"
        />

        <input
          type="password"
          name="contrasenia"
          value={form.contrasenia}
          onChange={handleChange}
          placeholder="Nueva contraseña"
          className="w-full border p-3 rounded"
        />

        <button
          type="submit"
          className="bg-[#960000] text-white px-6 py-3 rounded-lg"
        >
          Guardar cambios
        </button>

      </form>

    </div>
  );
}

export default PerfilAcademico;
