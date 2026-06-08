// src/components/GestionJornadaLaboral.jsx
import React, { useState, useEffect } from 'react';
import { jornadaService } from '../services/jornadaLaboralService.js';

export const RegistroJornada = ({ estudiante }) => {
  const estudianteId = estudiante?.id;
  
  const [jornadas, setJornadas] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [idEdicion, setIdEdicion] = useState(null);

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
    horaFin: ''
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
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    const formatearHora = (hora) => (hora.length === 5 ? `${hora}:00` : hora);

    const datosJornada = {
      estudianteId: estudianteId,
      diaSemana: formData.diaSemana,
      horaInicio: formatearHora(formData.horaInicio),
      horaFin: formData.horaFin
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
    } catch (err) {
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
  };

  // Filtrar jornadas según la búsqueda por día de la semana
  const jornadasFiltradas = jornadas.filter(j => 
    j.diaSemana.toLowerCase().includes(busqueda.toLowerCase())
  );

  return (
    
    <div className="w-full animate-fade-in">
    

      {/* */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* COLUMNA 1: Formulario Izquierdo */}
        <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm h-fit">
          <h2 className="text-lg font-bold text-[#430000] mb-4 border-b border-gray-100 pb-2">
            {idEdicion ? 'Editar Jornada' : 'Nueva Jornada'}
          </h2>
          
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1 tracking-wider">
                Día de la Semana
              </label>
              <select 
                name="diaSemana" 
                value={formData.diaSemana} 
                onChange={handleInputChange} 
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#430000]/20 focus:border-[#430000] transition-all"
              >
                {diasDeLaSemana.map(dia => (
                  <option key={dia} value={dia}>{dia}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1 tracking-wider">
                Hora Inicio
              </label>
              <input 
                type="time" 
                name="horaInicio" 
                value={formData.horaInicio} 
                onChange={handleInputChange} 
                required 
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#430000]/20 focus:border-[#430000] transition-all"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-gray-700 uppercase mb-1 tracking-wider">
                Hora Fin
              </label>
              <input 
                type="time" 
                name="horaFin" 
                value={formData.horaFin} 
                onChange={handleInputChange} 
                required 
                className="w-full p-2.5 bg-gray-50 border border-gray-200 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-[#430000]/20 focus:border-[#430000] transition-all"
              />
            </div>

            <div className="space-y-2 pt-2">
              <button 
                type="submit" 
                className={`w-full py-2.5 text-white rounded-lg font-bold text-sm tracking-wide shadow-sm transition-colors uppercase ${
                  idEdicion ? 'bg-amber-600 hover:bg-amber-700' : 'bg-[#430000] hover:bg-[#5a0000]'
                }`}
              >
                {idEdicion ? 'Actualizar Jornada' : 'Guardar Jornada'}
              </button>

              {idEdicion && (
                <button 
                  type="button"
                  onClick={resetFormulario}
                  className="w-full py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg font-semibold text-xs tracking-wide transition-colors uppercase"
                >
                  Cancelar Edición
                </button>
              )}
            </div>
          </form>
        </div>

        {/* COLUMNA 2 y 3: Contenedor Derecho de la Tabla */}
        <div className="bg-white p-6 rounded-2xl border border-gray-100 shadow-sm lg:col-span-2">
          
          {/* Cabecera Interna de la Tabla con el Buscador */}
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-4 border-b border-gray-100 pb-3">
            <h2 className="text-lg font-bold text-[#430000]">
              Jornadas Laborales Registradas
            </h2>
            
            <input 
              type="text"
              placeholder="Buscar por día (ej. Lunes)..."
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              className="p-2 px-3 bg-gray-50 border border-gray-200 rounded-lg text-sm w-full sm:w-60 focus:outline-none focus:ring-2 focus:ring-[#430000]/20 focus:border-[#430000] transition-all"
            />
          </div>
          
          {/* Tabla  */}
          <div className="overflow-x-auto rounded-xl border border-gray-100">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-100">
                  <th className="p-3.5 text-xs font-bold uppercase text-gray-500 tracking-wider">Día</th>
                  <th className="p-3.5 text-xs font-bold uppercase text-gray-500 tracking-wider">Horario</th>
                  <th className="p-3.5 text-xs font-bold uppercase text-gray-500 tracking-wider text-center">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50 text-sm">
                {loading ? (
                  <tr>
                    <td colSpan="3" className="p-6 text-center text-gray-400">Cargando jornadas...</td>
                  </tr>
                ) : jornadasFiltradas.length === 0 ? (
                  <tr>
                    <td colSpan="3" className="p-6 text-center text-gray-400">No se encontraron jornadas.</td>
                  </tr>
                ) : (
                  jornadasFiltradas.map((j) => (
                    <tr key={j.id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="p-3.5 font-medium text-gray-800 uppercase">{j.diaSemana}</td>
                      <td className="p-3.5 text-gray-600 font-mono bg-gray-50/30 rounded px-2 py-1 h-fit w-fit">
                        {j.horaInicio} - {j.horaFin}
                      </td>
                      <td className="p-3.5 text-center">
                        <button 
                          onClick={() => handleCargarParaEditar(j)}
                          className="px-3 py-1 text-xs font-semibold border border-gray-200 rounded-md text-gray-600 hover:bg-gray-50 transition-colors mr-2"
                        >
                          Editar
                        </button>
                        <button 
                          onClick={() => abrirModalEliminar(j.id, j.diaSemana)}
                          className="px-3 py-1 text-xs font-semibold bg-red-700 hover:bg-red-800 text-white rounded-md transition-colors"
                        >
                          Eliminar
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

      </div>

      {/* MODAL DE CONFIRMACIÓN */}
      {modalEliminar.isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm animate-fade-in">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-gray-100 transform scale-100 transition-all">
            <div className="flex items-start gap-4">
              <div className="p-2.5 bg-red-50 text-red-700 rounded-xl flex-shrink-0">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-6 h-6">
                  <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />
                </svg>
              </div>
              <div>
                <h3 className="text-lg font-bold text-gray-900 uppercase tracking-tight">
                  ¿Eliminar Jornada Laboral?
                </h3>
                <p className="text-sm text-gray-500 mt-2 leading-relaxed">
                  ¿Estás seguro de que deseas eliminar la jornada del día <span className="font-bold text-gray-800 uppercase">{modalEliminar.dia}</span>? Esta acción no se puede deshacer y borrará el registro de la base de datos.
                </p>
              </div>
            </div>
            <div className="mt-6 flex justify-end gap-3 border-t border-gray-100 pt-4">
              <button
                type="button"
                onClick={cerrarModalEliminar}
                className="px-4 py-2 text-xs font-bold text-gray-600 bg-gray-100 hover:bg-gray-200 rounded-lg uppercase tracking-wide transition-colors"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={confirmarEliminar}
                className="px-4 py-2 text-xs font-bold text-white bg-red-700 hover:bg-red-800 rounded-lg uppercase tracking-wide shadow-sm transition-colors"
              >
                Sí, Eliminar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};