import React from 'react';

function MateriaForm({ handleSubmit, codigo, setCodigo, nombre, setNombre, uv, setUv }) {
  return (
    <div className="lg:col-span-2 bg-[#1e293b]/40 border border-slate-800 p-6 rounded-xl shadow-2xl backdrop-blur-sm h-fit">
      <h4 className="text-xs font-bold text-white uppercase tracking-widest mb-5 flex items-center space-x-2 border-b border-slate-800 pb-3">
        <i className="fa-solid fa-square-plus text-pink-500 text-sm"></i>
        <span>Ingresar Nueva Materia</span>
      </h4>
      
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Código de asignatura</label>
          <div className="relative">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-500 text-xs">
              <i className="fa-solid fa-key"></i>
            </span>
            <input
              type="text"
              value={codigo}
              onChange={(e) => setCodigo(e.target.value)}
              placeholder="Ej: INF115"
              className="w-full text-sm pl-9 pr-4 py-2.5 bg-[#111827]/60 border border-slate-800 rounded-xl focus:ring-2 focus:ring-pink-500/20 focus:border-pink-500 text-white transition-all outline-none"
            />
          </div>
        </div>

        <div>
          <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Nombre completo</label>
          <div className="relative">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-500 text-xs">
              <i className="fa-solid fa-bookmark"></i>
            </span>
            <input
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              placeholder="Ej: Programación Orientada a Objetos"
              className="w-full text-sm pl-9 pr-4 py-2.5 bg-[#111827]/60 border border-slate-800 rounded-xl focus:ring-2 focus:ring-pink-500/20 focus:border-pink-500 text-white transition-all outline-none"
            />
          </div>
        </div>
        <div>
          <label className="block text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-1.5">Unidades Valorativas (UV)</label>
          <div className="relative">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-500 text-xs">
              <i className="fa-solid fa-hashtag"></i>
            </span>
            <input
              type="number"
              value={uv}
              onChange={(e) => setUv(e.target.value)}
              placeholder="Ej: 4"
              className="w-full text-sm pl-9 pr-4 py-2.5 bg-[#111827]/60 border border-slate-800 rounded-xl focus:ring-2 focus:ring-pink-500/20 focus:border-pink-500 text-white transition-all outline-none"
            />
          </div>
        </div>
        <button
          type="submit"
          className="w-full mt-3 flex items-center justify-center space-x-2 py-3 bg-sky-600 hover:bg-sky-500 active:bg-sky-700 text-white font-bold text-xs uppercase tracking-wider rounded-xl shadow-lg transition-all duration-150 cursor-pointer"
        >
          <i className="fa-solid fa-folder-plus"></i>
          <span>Agregar al Registro</span>
        </button>
      </form>
    </div>
  );
}

export default MateriaForm;