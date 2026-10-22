// src/components/GestionJornadaLaboral.jsx
import React, { useState, useEffect } from 'react';
import { jornadaService } from '../services/jornadaLaboralService.js';
import Aviso from './ui/Aviso';
import Boton from './ui/Boton';
import Campo from './ui/Campo';
import DialogoConfirmacion from './ui/DialogoConfirmacion';
import EncabezadoModulo from './ui/EncabezadoModulo';
import Modal from './ui/Modal';
import Selector from './ui/Selector';
import Tabla from './ui/Tabla';
import Tarjeta from './ui/Tarjeta';


export const RegistroJornada = ({ estudiante }) => {
  const estudianteId = estudiante?.id;
  
  const [jornadas, setJornadas] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [idEdicion, setIdEdicion] = useState(null);
  const [formularioAbierto, setFormularioAbierto] = useState(false);

  // Estado para la barra de búsqueda
  const [busqueda, setBusqueda] = useState('');

  // Estado para el modal de confirmación 
  const [modalEliminar, setModalEliminar] = useState({
    isOpen: false,
    id: null,
    dia: ''
  });

  const [formData, setFormData] = useState({
    diaSemana: 'Lunes',
    horaInicio: '',
    horaFin: '',
    estudianteId: estudianteId
  });

  const diasDeLaSemana = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

  const cargarJornadas = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await jornadaService.listarPorEstudiante(estudianteId);
      setJornadas(data);
    } catch (err) {
      setError('Error al conectar con el servidor.');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (estudianteId) {
      cargarJornadas();
    }
  }, [estudianteId]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData({ ...formData, [name]: value });
  };

  const resetFormulario = () => {
    setFormData({ diaSemana: 'Lunes', horaInicio: '', horaFin: '' });
    setIdEdicion(null);
    setFormularioAbierto(false);
  };

  const abrirFormularioNuevo = () => {
    resetFormulario();
    setFormularioAbierto(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    const formatearHora = (hora) => (hora.length === 5 ? `${hora}:00` : hora);

    const datosJornada = {
      estudianteId: estudianteId,
      diaSemana: formData.diaSemana,
      horaInicio: formatearHora(formData.horaInicio),
      horaFin: formatearHora(formData.horaFin)
    };

    try {
      if (idEdicion) {
        await jornadaService.actualizar(idEdicion, datosJornada);
      } else {
        await jornadaService.crear(datosJornada);
      }
      resetFormulario();
      cargarJornadas(); 
    } catch (err) {
      setError(idEdicion ? 'No se pudo actualizar la jornada.' : 'No se pudo guardar la jornada.');
      console.error(err);
    }
  };

  const abrirModalEliminar = (id, dia) => {
    setModalEliminar({ isOpen: true, id, dia });
  };

  const cerrarModalEliminar = () => {
    setModalEliminar({ isOpen: false, id: null, dia: '' });
  };

  const confirmarEliminar = async () => {
    try {
      setError(null);
      await jornadaService.eliminar(modalEliminar.id);
      if (idEdicion === modalEliminar.id) resetFormulario();
      cerrarModalEliminar();
      cargarJornadas();     
    } catch {
      setError('No se pudo eliminar la jornada de la base de datos.');
      cerrarModalEliminar();
    }
  };

  const handleCargarParaEditar = (jornada) => {
    const limpiarSegundos = (hora) => (hora ? hora.substring(0, 5) : '');
    setIdEdicion(jornada.id);
    setFormData({
      diaSemana: jornada.diaSemana,
      horaInicio: limpiarSegundos(jornada.horaInicio),
      horaFin: limpiarSegundos(jornada.horaFin)
    });
    setFormularioAbierto(true);
  };

  // Filtrar jornadas según la búsqueda por día de la semana
  const jornadasFiltradas = jornadas.filter(j => 
    j.diaSemana.toLowerCase().includes(busqueda.toLowerCase())
  );

  const filas = jornadasFiltradas.map((j) => ({
    id: j.id,
    celdas: [
      j.diaSemana,
      <>{j.horaInicio} - {j.horaFin}</>,
      <div className="flex justify-center gap-2">
        <Boton
          variante="secundario"
          tamano="sm"
          aria-label={`Editar jornada del ${j.diaSemana}, ${j.horaInicio} - ${j.horaFin}`}
          onClick={() => handleCargarParaEditar(j)}
        >
          Editar
        </Boton>
        <Boton
          variante="peligro"
          tamano="sm"
          aria-label={`Eliminar jornada del ${j.diaSemana}, ${j.horaInicio} - ${j.horaFin}`}
          onClick={() => abrirModalEliminar(j.id, j.diaSemana)}
        >
          Eliminar
        </Boton>
      </div>
    ]
  }));

  return (
    <div className="w-full animate-fade-in">
      <EncabezadoModulo
        className="mb-5"
        titulo="Jornada laboral"
        descripcion="Administra tus horarios de trabajo por día."
        acciones={
          <Boton icono="fa-solid fa-plus" onClick={abrirFormularioNuevo}>
            Nueva jornada
          </Boton>
        }
      />

      {error && (
        <Aviso tono="peligro" className="mb-4">
          {error}
        </Aviso>
      )}

      {/* Contenedor de la tabla */}
      <Tarjeta titulo="Jornadas Laborales Registradas">
        <Campo
          className="mb-4 w-full sm:w-60"
          required={false}
          placeholder="Buscar por día (ej. Lunes)..."
          aria-label="Buscar jornadas por día"
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
        />

        <Tabla
          columnas={[
            { titulo: 'Día', className: 'font-medium uppercase' },
            { titulo: 'Horario', className: 'whitespace-nowrap font-mono' },
            { titulo: 'Acciones', alinear: 'centro' }
          ]}
          filas={filas}
          cargando={loading}
          mensajeCarga="Cargando jornadas..."
          mensajeVacio="No se encontraron jornadas."
        />
      </Tarjeta>

      <Modal
        abierto={formularioAbierto}
        titulo={idEdicion ? 'Editar jornada laboral' : 'Nueva jornada laboral'}
        descripcion="Completa el día y las horas de tu jornada."
        etiquetaCerrar="Cerrar formulario"
        ancho="lg"
        onCerrar={resetFormulario}
      >
        {error && (
          <Aviso tono="peligro" className="mb-4">
            {error}
          </Aviso>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <Selector
            label="Día de la Semana"
            name="diaSemana"
            value={formData.diaSemana}
            onChange={handleInputChange}
            options={diasDeLaSemana}
            placeholder={null}
            required={false}
          />
          <Campo
            label="Hora Inicio"
            type="time"
            name="horaInicio"
            value={formData.horaInicio}
            onChange={handleInputChange}
          />
          <Campo
            label="Hora Fin"
            type="time"
            name="horaFin"
            value={formData.horaFin}
            onChange={handleInputChange}
          />

          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <Boton variante="secundario" onClick={resetFormulario}>
              Cancelar
            </Boton>
            <Boton type="submit">
              {idEdicion ? 'Actualizar Jornada' : 'Guardar Jornada'}
            </Boton>
          </div>
        </form>
      </Modal>

      {/* MODAL DE CONFIRMACIÓN: como antes, no se cierra con Escape ni pulsando fuera */}
      <DialogoConfirmacion
        abierto={modalEliminar.isOpen}
        titulo="¿Eliminar Jornada Laboral?"
        mensaje={
          <>
            ¿Estás seguro de que deseas eliminar la jornada del día{' '}
            <span className="font-bold uppercase text-ink-900">{modalEliminar.dia}</span>? Esta acción no se
            puede deshacer y borrará el registro de la base de datos.
          </>
        }
        textoConfirmar="Sí, Eliminar"
        onConfirmar={confirmarEliminar}
        onCancelar={cerrarModalEliminar}
        cerrarConEscape={false}
        cerrarAlPulsarFondo={false}
      />
    </div>
  );
};