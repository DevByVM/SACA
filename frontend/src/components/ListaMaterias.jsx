import React, { useState } from 'react';
import MateriasTable from './MateriasTable.jsx'; // Importa el nuevo componente de tabla
import MateriaForm from './MateriaForm.jsx';     // Importa el nuevo componente de formulario

function ListaMaterias({ vistaActiva }) {
  const [materias, setMaterias] = useState([
    { id: 1, codigo: 'INF115', nombre: 'Programación Orientada a Objetos', uv: 4 },
    { id: 2, codigo: 'MAT210', nombre: 'Matemáticas Discretas', uv: 3 },
    { id: 3, codigo: 'INF220', nombre: 'Estructura de Datos', uv: 4 },
  ]);

  const [codigo, setCodigo] = useState('');
  const [nombre, setNombre] = useState('');
  const [uv, setUv] = useState('');

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!codigo || !nombre || !uv) return;
    setMaterias([...materias, { id: Date.now(), codigo, nombre, uv: parseInt(uv) }]);
    setCodigo(''); setNombre(''); setUv('');
  };

  const totalUV = materias.reduce((acc, mat) => acc + mat.uv, 0);

  return (
    <div className="space-y-6 text-slate-300">
      {/* ENCABEZADO DEL MÓDULO */}
      <div className="flex items-center justify-between border-b border-slate-800 pb-4">
        <div>
          <h3 className="text-lg font-bold text-white">Asignaturas del Ciclo</h3>
          <p className="text-xs text-slate-500 mt-0.5">Listado oficial de materias registradas</p>
        </div>
        <div className="bg-pink-500/10 text-pink-400 px-3 py-1.5 rounded-xl text-xs font-semibold border border-pink-500/20 flex items-center space-x-1.5 shadow-sm">
          <i className="fa-solid fa-graduation-cap"></i>
          <span>Total UV: <strong className="text-sm font-black">{totalUV}</strong></span>
        </div>
      </div>

      {/* DISEÑO ADAPTABLE  */}
      <div className={`grid grid-cols-1 ${vistaActiva === "dashboard" ? "w-full" : "lg:grid-cols-5"} gap-6`}>
        
        {/* COLUMNA DE LA TABLA */}
        <div className={vistaActiva === "dashboard" ? "w-full" : "lg:col-span-3"}>
          <MateriasTable materias={materias} /> {/* Usando el nuevo componente */}
        </div>

        {/* COLUMNA DEL FORMULARIO */}
        {vistaActiva !== "dashboard" && (
          <MateriaForm 
            handleSubmit={handleSubmit}
            codigo={codigo}
            setCodigo={setCodigo}
            nombre={nombre}
            setNombre={setNombre}
            uv={uv}
            setUv={setUv}
          /> 
        )} 
      </div> 
    </div> 
  );
} 

export default ListaMaterias;