import React from 'react';

function MateriasTable({ materias }) {
  return (
    <div className="relative overflow-x-auto border border-slate-800 rounded-xl shadow-xl bg-[#1e293b]/20 backdrop-blur-sm">
      <table className="w-full text-sm text-left text-slate-400">
        <thead className="text-xs uppercase text-slate-300 bg-[#1e293b]/60 border-b border-slate-800">
          <tr>
            <th scope="col" className="px-5 py-3.5 font-semibold tracking-wider">Código</th>
            <th scope="col" className="px-5 py-3.5 font-semibold tracking-wider">Nombre de la Materia</th>
            <th scope="col" className="px-5 py-3.5 text-center font-semibold tracking-wider">UV</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/60 bg-[#111827]/40">
          {materias.map((materia) => (
            <tr key={materia.id} className="hover:bg-slate-800/40 transition-colors group">
              <td className="px-5 py-4 font-mono text-xs font-bold text-pink-400">
                {materia.codigo}
              </td>
              <td className="px-5 py-4 font-medium text-slate-200">
                {materia.nombre}
              </td>
              <td className="px-5 py-4 text-center font-semibold text-slate-300">
                {materia.uv}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default MateriasTable;