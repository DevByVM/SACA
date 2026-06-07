import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";

import { obtenerPerfil, actualizarPerfil } from "../services/perfilService";
import {
  obtenerPerfilAcademico,
  actualizarPerfilAcademico
} from "../services/perfilAcademicoService";

function PerfilAcademico({ estudiante }) {

  //  DATOS ESTUDIANTE (LOGIN)
  const [usuario, setUsuario] = useState({
    nombre: "",
    carnet: "",
    correoInstitucional: "",
    contrasenia: ""
  });


  const [academico, setAcademico] = useState({
    carrera: "",
    facultad: "",
    fechaIngreso: "",
    planEstudio: ""
  });

  const cargarDatos = useCallback(async () => {
    try {
      const dataUsuario = await obtenerPerfil();
      setUsuario({
        nombre: dataUsuario.nombre,
        carnet: dataUsuario.carnet,
        correoInstitucional: dataUsuario.correoInstitucional,
        contrasenia: ""
      });

      const dataAcademico = await obtenerPerfilAcademico(estudiante.id);

      if (dataAcademico) {
        setAcademico({
          carrera: dataAcademico.carrera || "",
          facultad: dataAcademico.facultad || "",
          fechaIngreso: dataAcademico.fechaIngreso || "",
          planEstudio: dataAcademico.planEstudio || ""
        });
      }

    } catch (error) {
      console.log("Error cargando perfil");
    }
  }, [estudiante]);

  useEffect(() => {
    cargarDatos();
  }, [cargarDatos]);

  const handleUserChange = (e) => {
    setUsuario({
      ...usuario,
      [e.target.name]: e.target.value
    });
  };

  const guardarUsuario = async (e) => {
    e.preventDefault();

    if (!usuario.nombre.trim()) {
      toast.warning("Nombre requerido");
      return;
    }

    if (!usuario.carnet.trim()) {
      toast.warning("Carnet requerido");
      return;
    }

    if (!usuario.correoInstitucional.endsWith("@ues.edu.sv")) {
      toast.warning("Correo institucional inválido");
      return;
    }

    if (
      usuario.contrasenia &&
      !usuario.contrasenia.match(/^(?=.*[A-Z])(?=.*\d).{8,}$/)
    ) {
      toast.warning("Contraseña débil");
      return;
    }

    try {
      const res = await actualizarPerfil(estudiante.id, usuario);
      toast.success(res);
    } catch (e) {
      toast.error("Error al actualizar usuario");
    }
  };

  //  PERFIL ACADÉMICO
  const handleAcadChange = (e) => {
    setAcademico({
      ...academico,
      [e.target.name]: e.target.value
    });
  };

  const guardarAcademico = async (e) => {
    e.preventDefault();

    if (!academico.carrera.trim()) {
      toast.warning("Carrera requerida");
      return;
    }

    if (!academico.facultad.trim()) {
      toast.warning("Facultad requerida");
      return;
    }

    try {
      const res = await actualizarPerfilAcademico(
        estudiante.id,
        academico
      );

      toast.success(res);

    } catch (e) {
      toast.error("Error al actualizar perfil académico");
    }
  };

  return (
  <div className="space-y-8">

    {/* TARJETA ESTUDIANTE */}
    <div className="bg-white border border-[#430000]/10 rounded-2xl shadow-sm p-6">

  <div className="flex items-start gap-4">

    <div className="w-16 h-16 rounded-full bg-[#960000]/10 flex items-center justify-center">
      <i className="fa-solid fa-user-graduate text-3xl text-[#960000]"></i>
    </div>

    <div className="flex-1">

      <h2 className="text-2xl font-bold text-[#430000]">
        {usuario.nombre || "Estudiante"}
      </h2>

      <p className="text-[#430000]/70">
        Carnet: {usuario.carnet}
      </p>

      <p className="text-[#430000]/60 text-sm mb-4">
       Correo: {usuario.correoInstitucional}
      </p>

      <div className="grid md:grid-cols-2 gap-3 text-sm">

        <div className="bg-[#960000]/5 rounded-lg p-3">
          <span className="font-semibold text-[#430000]">
            Carrera:
          </span>
          <br />
          {academico.carrera || "No registrada"}
        </div>

        <div className="bg-[#960000]/5 rounded-lg p-3">
          <span className="font-semibold text-[#430000]">
            Facultad:
          </span>
          <br />
          {academico.facultad || "No registrada"}
        </div>

        <div className="bg-[#960000]/5 rounded-lg p-3">
          <span className="font-semibold text-[#430000]">
            Fecha de ingreso:
          </span>
          <br />
          {academico.fechaIngreso || "No registrada"}
        </div>

        <div className="bg-[#960000]/5 rounded-lg p-3">
          <span className="font-semibold text-[#430000]">
            Plan de estudio:
          </span>
          <br />
          {academico.planEstudio || "No registrado"}
        </div>

      </div>

    </div>

  </div>

</div>

    {/* DATOS USUARIO */}
    <div className="bg-white border border-[#430000]/10 rounded-2xl shadow-sm overflow-hidden">

      <div className="bg-[#960000] px-6 py-4">

        <h2 className="text-white font-bold text-lg flex items-center gap-2">
          <i className="fa-solid fa-user"></i>
          Datos de Usuario
        </h2>

      </div>

      <form
        onSubmit={guardarUsuario}
        className="p-6 space-y-5"
      >

        <div className="grid md:grid-cols-2 gap-4">

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Nombre
            </label>

            <input
              name="nombre"
              value={usuario.nombre}
              onChange={handleUserChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Carnet
            </label>

            <input
              name="carnet"
              value={usuario.carnet}
              onChange={handleUserChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

        </div>

        <div className="grid md:grid-cols-2 gap-4">

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Correo Institucional
            </label>

            <input
              name="correoInstitucional"
              value={usuario.correoInstitucional}
              onChange={handleUserChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Nueva Contraseña
            </label>

            <input
              type="password"
              name="contrasenia"
              value={usuario.contrasenia}
              onChange={handleUserChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
              placeholder="Opcional"
            />
          </div>

        </div>

        <button
          type="submit"
          className="
            w-full
            bg-[#960000]
            hover:bg-[#7d0000]
            text-white
            font-bold
            py-3
            rounded-xl
            transition-all
            shadow-md
          "
        >
          Guardar Usuario
        </button>

      </form>

    </div>

    {/* DATOS ACADÉMICOS */}
    <div className="bg-white border border-[#430000]/10 rounded-2xl shadow-sm overflow-hidden">

      <div className="bg-[#960000] px-6 py-4">

        <h2 className="text-white font-bold text-lg flex items-center gap-2">
          <i className="fa-solid fa-book"></i>
          Datos Académicos
        </h2>

      </div>

      <form
        onSubmit={guardarAcademico}
        className="p-6 space-y-5"
      >

        <div className="grid md:grid-cols-2 gap-4">

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Carrera
            </label>

            <input
              name="carrera"
              value={academico.carrera}
              onChange={handleAcadChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Facultad
            </label>

            <input
              name="facultad"
              value={academico.facultad}
              onChange={handleAcadChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

        </div>

        <div className="grid md:grid-cols-2 gap-4">

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Fecha de Ingreso
            </label>

            <input
              type="date"
              name="fechaIngreso"
              value={academico.fechaIngreso}
              onChange={handleAcadChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

          <div>
            <label className="block mb-2 text-sm font-semibold text-[#430000]">
              Plan de Estudio
            </label>

            <input
              name="planEstudio"
              value={academico.planEstudio}
              onChange={handleAcadChange}
              className="w-full border border-[#430000]/20 rounded-xl p-3 focus:outline-none focus:ring-2 focus:ring-[#960000]"
            />
          </div>

        </div>

        <button
          type="submit"
          className="
            w-full
            bg-[#960000]
            hover:bg-[#7d0000]
            text-white
            font-bold
            py-3
            rounded-xl
            transition-all
            shadow-md
          "
        >
          Guardar Perfil Académico
        </button>

      </form>

    </div>

  </div>
);
}
export default PerfilAcademico;