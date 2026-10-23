import { useCallback, useEffect, useState } from "react";
import { toast } from "react-toastify";
import { obtenerPerfil, actualizarPerfil } from "../services/perfilService";
import {
  obtenerPerfilAcademico,
  actualizarPerfilAcademico
} from "../services/perfilAcademicoService";
import Boton from "./ui/Boton";
import Campo from "./ui/Campo";
import EncabezadoModulo from "./ui/EncabezadoModulo";
import Tarjeta from "./ui/Tarjeta";

function PerfilAcademico({ estudiante }) {

  //  DATOS ESTUDIANTE (LOGIN)
  const [usuario, setUsuario] = useState({
    nombre: "",
    carnet: "",
    correoInstitucional: "",
    contrasenia: "",
    contraseniaActual: ""
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
        contrasenia: "",
        contraseniaActual: ""
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

  if (usuario.contrasenia && !usuario.contraseniaActual) {
    toast.warning("Ingresa tu contraseña actual para cambiarla");
    return;
  }

  try {
    const res = await actualizarPerfil(
      estudiante.id,
      usuario
    );

    if (res === "Perfil actualizado correctamente") {
      toast.success(res);
    } else {
      toast.warning(res);
    }

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

    if (
      res === "Perfil académico actualizado correctamente"
    ) {
      toast.success(res);
    } else {
      toast.warning(res);
    }

  } catch (e) {
    toast.error("Error al actualizar perfil académico");
  }
};

  return (
  <section className="space-y-5">

    <EncabezadoModulo
      descripcion="Consulta y actualiza tus datos de usuario y tu información académica."
    />

    {/* RESUMEN DEL ESTUDIANTE */}
    <Tarjeta>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start">

        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
          <i className="fa-solid fa-user-graduate text-3xl" aria-hidden="true"></i>
        </div>

        <div className="min-w-0 flex-1">
          <h2 className="text-xl font-bold text-ink-900">
            {usuario.nombre || "Estudiante"}
          </h2>
          <p className="text-sm text-ink-600">Carnet: {usuario.carnet}</p>
          <p className="break-words text-sm text-ink-600">Correo: {usuario.correoInstitucional}</p>

          <dl className="mt-4 grid gap-3 text-sm md:grid-cols-2">
            {[
              ["Carrera", academico.carrera || "No registrada"],
              ["Facultad", academico.facultad || "No registrada"],
              ["Fecha de ingreso", academico.fechaIngreso || "No registrada"],
              ["Plan de estudio", academico.planEstudio || "No registrado"],
            ].map(([etiqueta, valor]) => (
              <div key={etiqueta} className="rounded-lg bg-surface-muted p-3">
                <dt className="font-semibold text-ink-900">{etiqueta}</dt>
                <dd className="mt-1 break-words text-ink-600">{valor}</dd>
              </div>
            ))}
          </dl>
        </div>

      </div>
    </Tarjeta>

    {/* DATOS USUARIO */}
    {/* Los campos no usan required: las validaciones con aviso de guardarUsuario siguen siendo las que deciden */}
    <Tarjeta titulo="Datos de usuario" nivelTitulo={2} icono="fa-solid fa-user">
      <form onSubmit={guardarUsuario} className="space-y-4">

        <div className="grid gap-4 md:grid-cols-2">
          <Campo
            label="Nombre"
            name="nombre"
            required={false}
            value={usuario.nombre}
            onChange={handleUserChange}
          />
          <Campo
            label="Carnet"
            name="carnet"
            required={false}
            value={usuario.carnet}
            onChange={handleUserChange}
          />
          <Campo
            className="md:col-span-2"
            label="Correo Institucional"
            name="correoInstitucional"
            required={false}
            value={usuario.correoInstitucional}
            onChange={handleUserChange}
          />
          <Campo
            label="Nueva Contraseña"
            type="password"
            name="contrasenia"
            required={false}
            value={usuario.contrasenia}
            onChange={handleUserChange}
            ayuda="Opcional. Mínimo 8 caracteres, con una mayúscula y un número."
          />
          <Campo
            label="Contraseña Actual"
            type="password"
            name="contraseniaActual"
            required={false}
            value={usuario.contraseniaActual}
            onChange={handleUserChange}
            ayuda="Obligatoria solo si cambias la contraseña."
          />
        </div>

        <div className="flex justify-end">
          <Boton type="submit" icono="fa-solid fa-floppy-disk" className="w-full sm:w-auto">
            Guardar Usuario
          </Boton>
        </div>

      </form>
    </Tarjeta>

    {/* DATOS ACADÉMICOS */}
    <Tarjeta titulo="Datos académicos" nivelTitulo={2} icono="fa-solid fa-book">
      <form onSubmit={guardarAcademico} className="space-y-4">

        <div className="grid gap-4 md:grid-cols-2">
          <Campo
            label="Carrera"
            name="carrera"
            required={false}
            value={academico.carrera}
            onChange={handleAcadChange}
          />
          <Campo
            label="Facultad"
            name="facultad"
            required={false}
            value={academico.facultad}
            onChange={handleAcadChange}
          />
          <Campo
            label="Fecha de Ingreso"
            type="date"
            name="fechaIngreso"
            required={false}
            value={academico.fechaIngreso}
            onChange={handleAcadChange}
          />
          <Campo
            label="Plan de Estudio"
            name="planEstudio"
            required={false}
            value={academico.planEstudio}
            onChange={handleAcadChange}
          />
        </div>

        <div className="flex justify-end">
          <Boton type="submit" icono="fa-solid fa-floppy-disk" className="w-full sm:w-auto">
            Guardar Perfil Académico
          </Boton>
        </div>

      </form>
    </Tarjeta>

  </section>
);
}
export default PerfilAcademico;