import { useCallback, useEffect, useState } from 'react'
import './App.css'
import {
  actualizarCiclo,
  crearCiclo,
  eliminarCiclo,
  listarCiclos,
} from './api/ciclosApi'
import {
  actualizarHorario,
  crearHorario,
  eliminarHorario,
  listarHorarios,
} from './api/horariosApi'
import {
  actualizarMateria,
  crearMateria,
  eliminarMateria,
  listarMaterias,
} from './api/materiasApi'

const cicloInicial = {
  nombre: '',
  anio: new Date().getFullYear(),
  fechaInicio: '',
  fechaFin: '',
  estado: 'ACTIVO',
}

const materiaInicial = {
  cicloAcademicoId: '',
  nombre: '',
  codigo: '',
  grupoTeorico: '',
}

const horarioInicial = {
  materiaInscritaId: '',
  diaSemana: 'LUNES',
  horaInicio: '',
  horaFin: '',
  modalidad: 'PRESENCIAL',
  docenteTutor: '',
}

function App() {
  const [estudianteId, setEstudianteId] = useState('1')
  const [seccionActiva, setSeccionActiva] = useState('ciclos')
  const [mensaje, setMensaje] = useState(null)
  const [cargando, setCargando] = useState(false)

  const [ciclos, setCiclos] = useState([])
  const [materias, setMaterias] = useState([])
  const [horarios, setHorarios] = useState([])

  const [cicloForm, setCicloForm] = useState(cicloInicial)
  const [materiaForm, setMateriaForm] = useState(materiaInicial)
  const [horarioForm, setHorarioForm] = useState(horarioInicial)

  const [cicloEditando, setCicloEditando] = useState(null)
  const [materiaEditando, setMateriaEditando] = useState(null)
  const [horarioEditando, setHorarioEditando] = useState(null)

  const estudianteValido = estudianteId.trim() !== ''

  const cargarDatos = useCallback(async () => {
    try {
      setCargando(true)
      setMensaje(null)
      const [ciclosData, materiasData, horariosData] = await Promise.all([
        listarCiclos(estudianteId),
        listarMaterias(estudianteId),
        listarHorarios(estudianteId),
      ])
      setCiclos(ciclosData)
      setMaterias(materiasData)
      setHorarios(horariosData)
    } catch (error) {
      mostrarError(error.message)
    } finally {
      setCargando(false)
    }
  }, [estudianteId])

  useEffect(() => {
    if (!estudianteValido) return
    cargarDatos()
  }, [cargarDatos, estudianteValido])

  function mostrarExito(texto) {
    setMensaje({ tipo: 'exito', texto })
  }

  function mostrarError(texto) {
    setMensaje({ tipo: 'error', texto })
  }

  function limpiarCiclo() {
    setCicloForm(cicloInicial)
    setCicloEditando(null)
  }

  function limpiarMateria() {
    setMateriaForm({
      ...materiaInicial,
      cicloAcademicoId: ciclos[0]?.id ?? '',
    })
    setMateriaEditando(null)
  }

  function limpiarHorario() {
    setHorarioForm({
      ...horarioInicial,
      materiaInscritaId: materias[0]?.id ?? '',
    })
    setHorarioEditando(null)
  }

  async function guardarCiclo(event) {
    event.preventDefault()
    try {
      const payload = {
        ...cicloForm,
        estudianteId: Number(estudianteId),
        anio: Number(cicloForm.anio),
      }
      if (cicloEditando) {
        await actualizarCiclo(cicloEditando, estudianteId, payload)
        mostrarExito('Ciclo academico actualizado')
      } else {
        await crearCiclo(payload)
        mostrarExito('Ciclo academico registrado')
      }
      limpiarCiclo()
      await cargarDatos()
    } catch (error) {
      mostrarError(error.message)
    }
  }

  async function guardarMateria(event) {
    event.preventDefault()
    try {
      const payload = {
        ...materiaForm,
        cicloAcademicoId: Number(materiaForm.cicloAcademicoId),
      }
      if (materiaEditando) {
        await actualizarMateria(materiaEditando, estudianteId, payload)
        mostrarExito('Materia inscrita actualizada')
      } else {
        await crearMateria(estudianteId, payload)
        mostrarExito('Materia inscrita registrada')
      }
      limpiarMateria()
      await cargarDatos()
    } catch (error) {
      mostrarError(error.message)
    }
  }

  async function guardarHorario(event) {
    event.preventDefault()
    try {
      const payload = {
        ...horarioForm,
        materiaInscritaId: Number(horarioForm.materiaInscritaId),
      }
      if (horarioEditando) {
        await actualizarHorario(horarioEditando, estudianteId, payload)
        mostrarExito('Horario de clase actualizado')
      } else {
        await crearHorario(estudianteId, payload)
        mostrarExito('Horario de clase registrado')
      }
      limpiarHorario()
      await cargarDatos()
    } catch (error) {
      mostrarError(error.message)
    }
  }

  function editarCiclo(ciclo) {
    setSeccionActiva('ciclos')
    setCicloEditando(ciclo.id)
    setCicloForm({
      nombre: ciclo.nombre,
      anio: ciclo.anio,
      fechaInicio: ciclo.fechaInicio,
      fechaFin: ciclo.fechaFin,
      estado: ciclo.estado,
    })
  }

  function editarMateria(materia) {
    setSeccionActiva('materias')
    setMateriaEditando(materia.id)
    setMateriaForm({
      cicloAcademicoId: materia.cicloAcademicoId,
      nombre: materia.nombre,
      codigo: materia.codigo,
      grupoTeorico: materia.grupoTeorico,
    })
  }

  function editarHorario(horario) {
    setSeccionActiva('horarios')
    setHorarioEditando(horario.id)
    setHorarioForm({
      materiaInscritaId: horario.materiaInscritaId,
      diaSemana: horario.diaSemana,
      horaInicio: horario.horaInicio,
      horaFin: horario.horaFin,
      modalidad: horario.modalidad,
      docenteTutor: horario.docenteTutor ?? '',
    })
  }

  async function eliminarRegistro(tipo, id) {
    try {
      if (tipo === 'ciclo') {
        await eliminarCiclo(id, estudianteId)
        mostrarExito('Ciclo academico eliminado')
      }
      if (tipo === 'materia') {
        await eliminarMateria(id, estudianteId)
        mostrarExito('Materia inscrita eliminada')
      }
      if (tipo === 'horario') {
        await eliminarHorario(id, estudianteId)
        mostrarExito('Horario de clase eliminado')
      }
      await cargarDatos()
    } catch (error) {
      mostrarError(error.message)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Universidad de El Salvador</p>
          <h1>SACA</h1>
          <p className="subtitle">Gestion academica de ciclos, materias y horarios</p>
        </div>
        <label className="student-control">
          <span>Estudiante ID</span>
          <input
            type="number"
            min="1"
            value={estudianteId}
            onChange={(event) => setEstudianteId(event.target.value)}
          />
        </label>
      </header>

      <nav className="tabs" aria-label="Secciones">
        {[
          ['ciclos', 'Ciclos'],
          ['materias', 'Materias'],
          ['horarios', 'Horarios'],
        ].map(([id, label]) => (
          <button
            key={id}
            className={seccionActiva === id ? 'active' : ''}
            type="button"
            onClick={() => setSeccionActiva(id)}
          >
            {label}
          </button>
        ))}
      </nav>

      {mensaje && <div className={`alert ${mensaje.tipo}`}>{mensaje.texto}</div>}

      <section className="summary-grid">
        <div>
          <strong>{ciclos.length}</strong>
          <span>Ciclos</span>
        </div>
        <div>
          <strong>{materias.length}</strong>
          <span>Materias</span>
        </div>
        <div>
          <strong>{horarios.length}</strong>
          <span>Horarios</span>
        </div>
      </section>

      {cargando && <div className="loading">Cargando informacion academica...</div>}

      {seccionActiva === 'ciclos' && (
        <section className="workspace">
          <FormularioCiclo
            form={cicloForm}
            editando={Boolean(cicloEditando)}
            onChange={setCicloForm}
            onSubmit={guardarCiclo}
            onCancel={limpiarCiclo}
          />
          <ListadoCiclos
            ciclos={ciclos}
            onEdit={editarCiclo}
            onDelete={(id) => eliminarRegistro('ciclo', id)}
          />
        </section>
      )}

      {seccionActiva === 'materias' && (
        <section className="workspace">
          <FormularioMateria
            ciclos={ciclos}
            form={materiaForm}
            editando={Boolean(materiaEditando)}
            onChange={setMateriaForm}
            onSubmit={guardarMateria}
            onCancel={limpiarMateria}
          />
          <ListadoMaterias
            materias={materias}
            onEdit={editarMateria}
            onDelete={(id) => eliminarRegistro('materia', id)}
          />
        </section>
      )}

      {seccionActiva === 'horarios' && (
        <section className="workspace">
          <FormularioHorario
            materias={materias}
            form={horarioForm}
            editando={Boolean(horarioEditando)}
            onChange={setHorarioForm}
            onSubmit={guardarHorario}
            onCancel={limpiarHorario}
          />
          <ListadoHorarios
            horarios={horarios}
            onEdit={editarHorario}
            onDelete={(id) => eliminarRegistro('horario', id)}
          />
        </section>
      )}
    </main>
  )
}

function FormularioCiclo({ form, editando, onChange, onSubmit, onCancel }) {
  return (
    <form className="panel form-panel" onSubmit={onSubmit}>
      <PanelHeader titulo={editando ? 'Editar ciclo' : 'Registrar ciclo'} />
      <FormField label="Nombre">
        <input
          required
          value={form.nombre}
          onChange={(event) => onChange({ ...form, nombre: event.target.value })}
        />
      </FormField>
      <div className="form-grid">
        <FormField label="Anio">
          <input
            required
            min="2000"
            max="2100"
            type="number"
            value={form.anio}
            onChange={(event) => onChange({ ...form, anio: event.target.value })}
          />
        </FormField>
        <FormField label="Estado">
          <select
            value={form.estado}
            onChange={(event) => onChange({ ...form, estado: event.target.value })}
          >
            <option value="PLANIFICADO">Planificado</option>
            <option value="ACTIVO">Activo</option>
            <option value="FINALIZADO">Finalizado</option>
          </select>
        </FormField>
      </div>
      <div className="form-grid">
        <FormField label="Fecha inicio">
          <input
            required
            type="date"
            value={form.fechaInicio}
            onChange={(event) => onChange({ ...form, fechaInicio: event.target.value })}
          />
        </FormField>
        <FormField label="Fecha fin">
          <input
            required
            type="date"
            value={form.fechaFin}
            onChange={(event) => onChange({ ...form, fechaFin: event.target.value })}
          />
        </FormField>
      </div>
      <FormActions editando={editando} onCancel={onCancel} />
    </form>
  )
}

function FormularioMateria({ ciclos, form, editando, onChange, onSubmit, onCancel }) {
  return (
    <form className="panel form-panel" onSubmit={onSubmit}>
      <PanelHeader titulo={editando ? 'Editar materia' : 'Registrar materia'} />
      <FormField label="Ciclo academico">
        <select
          required
          disabled={editando}
          value={form.cicloAcademicoId}
          onChange={(event) => onChange({ ...form, cicloAcademicoId: event.target.value })}
        >
          <option value="">Seleccione un ciclo</option>
          {ciclos.map((ciclo) => (
            <option key={ciclo.id} value={ciclo.id}>
              {ciclo.nombre} - {ciclo.anio}
            </option>
          ))}
        </select>
      </FormField>
      <FormField label="Nombre">
        <input
          required
          value={form.nombre}
          onChange={(event) => onChange({ ...form, nombre: event.target.value })}
        />
      </FormField>
      <div className="form-grid">
        <FormField label="Codigo">
          <input
            required
            value={form.codigo}
            onChange={(event) => onChange({ ...form, codigo: event.target.value })}
          />
        </FormField>
        <FormField label="Grupo teorico">
          <input
            required
            value={form.grupoTeorico}
            onChange={(event) => onChange({ ...form, grupoTeorico: event.target.value })}
          />
        </FormField>
      </div>
      <FormActions editando={editando} onCancel={onCancel} />
    </form>
  )
}

function FormularioHorario({ materias, form, editando, onChange, onSubmit, onCancel }) {
  return (
    <form className="panel form-panel" onSubmit={onSubmit}>
      <PanelHeader titulo={editando ? 'Editar horario' : 'Registrar horario'} />
      <FormField label="Materia">
        <select
          required
          disabled={editando}
          value={form.materiaInscritaId}
          onChange={(event) => onChange({ ...form, materiaInscritaId: event.target.value })}
        >
          <option value="">Seleccione una materia</option>
          {materias.map((materia) => (
            <option key={materia.id} value={materia.id}>
              {materia.codigo} - {materia.nombre}
            </option>
          ))}
        </select>
      </FormField>
      <div className="form-grid">
        <FormField label="Dia">
          <select
            value={form.diaSemana}
            onChange={(event) => onChange({ ...form, diaSemana: event.target.value })}
          >
            {['LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO', 'DOMINGO'].map(
              (dia) => (
                <option key={dia} value={dia}>
                  {dia}
                </option>
              ),
            )}
          </select>
        </FormField>
        <FormField label="Modalidad">
          <select
            value={form.modalidad}
            onChange={(event) => onChange({ ...form, modalidad: event.target.value })}
          >
            <option value="PRESENCIAL">Presencial</option>
            <option value="VIRTUAL">Virtual</option>
            <option value="HIBRIDA">Hibrida</option>
          </select>
        </FormField>
      </div>
      <div className="form-grid">
        <FormField label="Hora inicio">
          <input
            required
            type="time"
            value={form.horaInicio}
            onChange={(event) => onChange({ ...form, horaInicio: event.target.value })}
          />
        </FormField>
        <FormField label="Hora fin">
          <input
            required
            type="time"
            value={form.horaFin}
            onChange={(event) => onChange({ ...form, horaFin: event.target.value })}
          />
        </FormField>
      </div>
      <FormField label="Docente o tutor">
        <input
          value={form.docenteTutor}
          onChange={(event) => onChange({ ...form, docenteTutor: event.target.value })}
        />
      </FormField>
      <FormActions editando={editando} onCancel={onCancel} />
    </form>
  )
}

function ListadoCiclos({ ciclos, onEdit, onDelete }) {
  return (
    <section className="panel list-panel">
      <PanelHeader titulo="Ciclos registrados" />
      {ciclos.length === 0 ? (
        <EmptyState texto="No hay ciclos registrados" />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Anio</th>
                <th>Periodo</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {ciclos.map((ciclo) => (
                <tr key={ciclo.id}>
                  <td>{ciclo.nombre}</td>
                  <td>{ciclo.anio}</td>
                  <td>
                    {ciclo.fechaInicio} a {ciclo.fechaFin}
                  </td>
                  <td>
                    <span className="badge">{ciclo.estado}</span>
                  </td>
                  <td>
                    <RowActions onEdit={() => onEdit(ciclo)} onDelete={() => onDelete(ciclo.id)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function ListadoMaterias({ materias, onEdit, onDelete }) {
  return (
    <section className="panel list-panel">
      <PanelHeader titulo="Materias inscritas" />
      {materias.length === 0 ? (
        <EmptyState texto="No hay materias registradas" />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Codigo</th>
                <th>Materia</th>
                <th>Ciclo</th>
                <th>Grupo</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {materias.map((materia) => (
                <tr key={materia.id}>
                  <td>{materia.codigo}</td>
                  <td>{materia.nombre}</td>
                  <td>{materia.cicloAcademicoNombre}</td>
                  <td>{materia.grupoTeorico}</td>
                  <td>
                    <RowActions
                      onEdit={() => onEdit(materia)}
                      onDelete={() => onDelete(materia.id)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function ListadoHorarios({ horarios, onEdit, onDelete }) {
  return (
    <section className="panel list-panel">
      <PanelHeader titulo="Horarios de clase" />
      {horarios.length === 0 ? (
        <EmptyState texto="No hay horarios registrados" />
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Dia</th>
                <th>Horario</th>
                <th>Materia</th>
                <th>Modalidad</th>
                <th>Docente</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {horarios.map((horario) => (
                <tr key={horario.id}>
                  <td>{horario.diaSemana}</td>
                  <td>
                    {horario.horaInicio} - {horario.horaFin}
                  </td>
                  <td>{horario.materiaNombre}</td>
                  <td>
                    <span className="badge">{horario.modalidad}</span>
                  </td>
                  <td>{horario.docenteTutor || 'Sin asignar'}</td>
                  <td>
                    <RowActions
                      onEdit={() => onEdit(horario)}
                      onDelete={() => onDelete(horario.id)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function PanelHeader({ titulo }) {
  return <h2>{titulo}</h2>
}

function FormField({ label, children }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  )
}

function FormActions({ editando, onCancel }) {
  return (
    <div className="actions">
      <button className="primary" type="submit">
        {editando ? 'Guardar cambios' : 'Registrar'}
      </button>
      {editando && (
        <button type="button" onClick={onCancel}>
          Cancelar
        </button>
      )}
    </div>
  )
}

function RowActions({ onEdit, onDelete }) {
  return (
    <div className="row-actions">
      <button type="button" onClick={onEdit}>
        Editar
      </button>
      <button className="danger" type="button" onClick={onDelete}>
        Eliminar
      </button>
    </div>
  )
}

function EmptyState({ texto }) {
  return <p className="empty">{texto}</p>
}

export default App
