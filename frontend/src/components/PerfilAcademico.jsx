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

  // =========================
 
  // =========================
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

  // =========================
  //  PERFIL ACADÉMICO
  // =========================
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

      {/* 🟦 PERFIL USUARIO */}
      <div className="bg-white p-6 rounded-xl shadow">
        <h2 className="text-xl font-bold mb-4">Datos de Usuario</h2>

        <form onSubmit={guardarUsuario} className="space-y-4">

          <input name="nombre" value={usuario.nombre} onChange={handleUserChange}
            className="w-full border p-3 rounded" placeholder="Nombre" />

          <input name="carnet" value={usuario.carnet} onChange={handleUserChange}
            className="w-full border p-3 rounded" placeholder="Carnet" />

          <input name="correoInstitucional" value={usuario.correoInstitucional}
            onChange={handleUserChange}
            className="w-full border p-3 rounded" placeholder="Correo" />

          <input name="contrasenia" type="password"
            value={usuario.contrasenia}
            onChange={handleUserChange}
            className="w-full border p-3 rounded"
            placeholder="Nueva contraseña" />

          <button className="w-full bg-[#960000] text-white p-3 rounded">
            Guardar Usuario
          </button>

        </form>
      </div>

      {/*  PERFIL ACADÉMICO */}
      <div className="bg-white p-6 rounded-xl shadow">
        <h2 className="text-xl font-bold mb-4">Datos Académicos</h2>

        <form onSubmit={guardarAcademico} className="space-y-4">

          <input name="carrera" value={academico.carrera}
            onChange={handleAcadChange}
            className="w-full border p-3 rounded"
            placeholder="Carrera" />

          <input name="facultad" value={academico.facultad}
            onChange={handleAcadChange}
            className="w-full border p-3 rounded"
            placeholder="Facultad" />

          <input type="date" name="fechaIngreso"
            value={academico.fechaIngreso}
            onChange={handleAcadChange}
            className="w-full border p-3 rounded" />

          <input name="planEstudio" value={academico.planEstudio}
            onChange={handleAcadChange}
            className="w-full border p-3 rounded"
            placeholder="Plan de estudio" />

          <button className="w-full bg-[#430000] text-white p-3 rounded">
            Guardar Perfil Académico
          </button>

        </form>
      </div>

    </div>
  );
}

export default PerfilAcademico;