import React from 'react';

function MateriasTable({ materias }) {
  return (
    <div className="relative overflow-x-auto border border-[#430000]/20 rounded-xl shadow-sm bg-[#ffffff]">
      <table className="w-full text-sm text-left text-[#430000]">

        <thead className="text-xs uppercase text-[#430000] bg-[#960000]/10 border-b border-[#430000]/20">
          <tr>
            <th
              scope="col"
              className="px-5 py-3.5 font-semibold tracking-wider"
            >
              Código
            </th>

            <th
              scope="col"
              className="px-5 py-3.5 font-semibold tracking-wider"
            >
              Nombre de la Materia
            </th>

            <th
              scope="col"
              className="px-5 py-3.5 text-center font-semibold tracking-wider"
            >
              UV
            </th>
          </tr>
        </thead>

        <tbody className="divide-y divide-[#430000]/10 bg-[#ffffff]">
          {materias.map((materia) => (
            <tr
              key={materia.id}
              className="hover:bg-[#960000]/5 transition-colors"
            >
              <td className="px-5 py-4 font-mono text-xs font-bold text-[#960000]">
                {materia.codigo}
              </td>

              <td className="px-5 py-4 font-medium text-[#430000]">
                {materia.nombre}
              </td>

              <td className="px-5 py-4 text-center font-semibold text-[#430000]">
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