import { useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";
import { toast } from "react-toastify";

import { confirmarHorario, obtenerHorarioConfirmado, obtenerHorarioSugerido } from "../../api/horarioEstudioApi";
import { listarCiclos } from "../../api/ciclosApi";
import { listarMaterias } from "../../api/materiasApi";
import {
  aSolicitud,
  bloquesDesdeHorario,
  bloquesDesdeSugerencia,
  diasDeLaSemana,
  firmaBloques,
  formatearDuracion,
  formatearFecha,
  hayCambiosSinGuardar,
  minutosDe,
  minutosTotales,
  nuevoBloque,
  ordenarBloques,
  SITUACION,
  situacionPropuesta,
  sumarDias,
  textosAdvertencias,
  validarBloque,
} from "../../utils/horarioEstudio";
import Aviso from "../ui/Aviso";
import Boton from "../ui/Boton";
import Campo from "../ui/Campo";
import DialogoConfirmacion from "../ui/DialogoConfirmacion";
import EncabezadoModulo from "../ui/EncabezadoModulo";
import EstadoVacio from "../ui/EstadoVacio";
import Modal from "../ui/Modal";
import Selector from "../ui/Selector";
import Tarjeta from "../ui/Tarjeta";

// Materias de los ciclos activos para agregar bloques; si falla se usan las de la sugerencia
async function cargarMateriasActivas(estudianteId) {
  if (!estudianteId) return [];
  const [ciclos, materias] = await Promise.all([listarCiclos(estudianteId), listarMaterias(estudianteId)]);
  const activos = new Set(ciclos.filter((ciclo) => ciclo.estado === "ACTIVO").map((ciclo) => ciclo.id));
  return materias
    .filter((materia) => activos.has(materia.cicloAcademicoId))
    .map((materia) => ({ id: materia.id, nombre: materia.nombre }));
}

function Estadistica({ etiqueta, valor }) {
  return (
    <div className="rounded-lg bg-surface-muted p-3">
      <p className="text-xs font-bold uppercase tracking-widest text-ink-600">{etiqueta}</p>
      <p className="mt-1 text-xl font-bold text-ink-900">{valor}</p>
    </div>
  );
}

// Tabla del horario confirmado; la misma se usa en pantalla y en la hoja impresa
function TablaHorario({ horario }) {
  const dias = diasDeLaSemana(horario.desde);
  const porMateria = Object.values(
    horario.bloques.reduce((acumulado, bloque) => {
      const actual = acumulado[bloque.materiaInscritaId]
        ?? { id: bloque.materiaInscritaId, materia: bloque.materia, minutos: 0, sesiones: 0 };
      actual.minutos += bloque.minutos;
      actual.sesiones += 1;
      acumulado[bloque.materiaInscritaId] = actual;
      return acumulado;
    }, {})
  ).sort((a, b) => b.minutos - a.minutos);

  return (
    <div className="space-y-6">
      <div className="overflow-x-auto">
        <table className="tabla-horario w-full border-collapse text-left text-sm">
          <caption className="sr-only">Bloques de estudio por día</caption>
          <thead>
            <tr className="border-b border-line text-xs uppercase tracking-widest text-ink-600">
              <th scope="col" className="py-2 pr-3">Día</th>
              <th scope="col" className="py-2 pr-3">Horario</th>
              <th scope="col" className="py-2 pr-3">Materia</th>
              <th scope="col" className="py-2 text-right">Duración</th>
            </tr>
          </thead>
          <tbody>
            {dias.map((dia) => {
              const bloques = horario.bloques.filter((bloque) => bloque.fecha === dia.fecha);
              if (bloques.length === 0) {
                return (
                  <tr key={dia.fecha} className="border-b border-line">
                    <th scope="row" className="py-2 pr-3 font-semibold">{dia.nombre} {formatearFecha(dia.fecha)}</th>
                    <td colSpan={3} className="py-2 text-ink-600">Sin bloques de estudio</td>
                  </tr>
                );
              }
              return bloques.map((bloque, indice) => (
                <tr key={bloque.id ?? `${dia.fecha}-${indice}`} className="border-b border-line">
                  {indice === 0 && (
                    <th scope="row" rowSpan={bloques.length} className="py-2 pr-3 align-top font-semibold">
                      {dia.nombre} {formatearFecha(dia.fecha)}
                    </th>
                  )}
                  <td className="py-2 pr-3 whitespace-nowrap">
                    {bloque.inicio.slice(0, 5)} – {bloque.fin.slice(0, 5)}
                  </td>
                  <td className="py-2 pr-3">{bloque.materia}</td>
                  <td className="py-2 text-right whitespace-nowrap">{formatearDuracion(bloque.minutos)}</td>
                </tr>
              ));
            })}
          </tbody>
        </table>
      </div>

      <div className="overflow-x-auto">
        <table className="tabla-horario w-full border-collapse text-left text-sm">
          <caption className="mb-2 text-left text-sm font-semibold text-ink-900">Total por materia</caption>
          <thead>
            <tr className="border-b border-line text-xs uppercase tracking-widest text-ink-600">
              <th scope="col" className="py-2 pr-3">Materia</th>
              <th scope="col" className="py-2 pr-3 text-right">Sesiones</th>
              <th scope="col" className="py-2 text-right">Tiempo</th>
            </tr>
          </thead>
          <tbody>
            {porMateria.map((fila) => (
              <tr key={fila.id} className="border-b border-line">
                <td className="py-2 pr-3">{fila.materia}</td>
                <td className="py-2 pr-3 text-right">{fila.sesiones}</td>
                <td className="py-2 text-right">{formatearDuracion(fila.minutos)}</td>
              </tr>
            ))}
            <tr>
              <th scope="row" className="py-2 pr-3 text-left">Total semanal</th>
              <td className="py-2 pr-3 text-right">{horario.bloques.length}</td>
              <td className="py-2 text-right font-semibold">{formatearDuracion(horario.minutosTotales)}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

function textoConfirmadoEn(confirmadoEn) {
  if (!confirmadoEn) return "";
  const [fecha, hora] = confirmadoEn.split("T");
  return `${formatearFecha(fecha)}/${fecha.slice(0, 4)} a las ${hora.slice(0, 5)}`;
}

function HorarioEstudio({ estudiante, onNavegar }) {
  const [carga, setCarga] = useState({ cargando: true, error: "" });
  const [recarga, setRecarga] = useState(0);
  const [sugerencia, setSugerencia] = useState(null);
  const [confirmado, setConfirmado] = useState(null);
  // null = no se está editando ninguna propuesta
  const [borrador, setBorrador] = useState(null);
  // Huella de los bloques con los que empezó la edición, para saber si hay cambios sin guardar
  const [firmaOriginal, setFirmaOriginal] = useState(() => firmaBloques([]));
  const [descartada, setDescartada] = useState(false);
  const [materiasActivas, setMateriasActivas] = useState([]);
  const [edicion, setEdicion] = useState(null);
  const [errorBloque, setErrorBloque] = useState("");
  const [dialogo, setDialogo] = useState(null);
  const [guardando, setGuardando] = useState(false);
  const [errorGuardar, setErrorGuardar] = useState("");

  const estudianteId = estudiante?.id;

  useEffect(() => {
    let activo = true;
    (async () => {
      try {
        const propuesta = await obtenerHorarioSugerido();
        const guardado = propuesta.dias.length > 0
          ? await obtenerHorarioConfirmado(propuesta.periodo.desde)
          : null;
        const materias = await cargarMateriasActivas(estudianteId).catch(() => []);
        if (!activo) return;
        setSugerencia(propuesta);
        setConfirmado(guardado);
        // Sin materias en el ciclo activo no hay nada que editar: no se abre ningún borrador
        const sinMaterias = situacionPropuesta(propuesta) === SITUACION.SIN_MATERIAS;
        const inicial = guardado || sinMaterias ? null : bloquesDesdeSugerencia(propuesta);
        setBorrador(inicial);
        setFirmaOriginal(firmaBloques(inicial ?? []));
        setMateriasActivas(materias);
        setDescartada(false);
        setErrorGuardar("");
        setCarga({ cargando: false, error: "" });
      } catch (error) {
        if (activo) setCarga({ cargando: false, error: error.message || "No se pudo cargar la propuesta." });
      }
    })();
    return () => {
      activo = false;
    };
  }, [recarga, estudianteId]);

  const desde = sugerencia?.periodo?.desde;
  const dias = useMemo(() => (desde ? diasDeLaSemana(desde) : []), [desde]);

  // Opciones de materia: las de los ciclos activos más cualquiera que ya aparezca en la propuesta
  const opcionesMateria = useMemo(() => {
    const porId = new Map(materiasActivas.map((materia) => [materia.id, materia.nombre]));
    (sugerencia?.dias ?? []).forEach((dia) =>
      dia.bloques.forEach((bloque) => porId.set(bloque.materiaInscritaId, bloque.materia))
    );
    (sugerencia?.sinAsignar ?? []).forEach((materia) => porId.set(materia.materiaInscritaId, materia.materia));
    (borrador ?? []).forEach((bloque) => porId.set(bloque.materiaInscritaId, bloque.materia));
    return [...porId.entries()]
      .map(([id, nombre]) => ({ value: String(id), label: nombre }))
      .sort((a, b) => a.label.localeCompare(b.label, "es"));
  }, [materiasActivas, sugerencia, borrador]);

  function recargar() {
    setCarga({ cargando: true, error: "" });
    setRecarga((actual) => actual + 1);
  }

  // Con cambios sin guardar pide confirmación; si no los hay, actualiza directamente
  function pedirActualizacion() {
    if (hayCambiosSinGuardar(borrador, firmaOriginal)) {
      setDialogo("actualizar");
      return;
    }
    recargar();
  }

  function editarDesde(bloques) {
    setBorrador(bloques);
    setFirmaOriginal(firmaBloques(bloques));
    setDescartada(false);
    setErrorGuardar("");
  }

  function abrirNuevo(fecha) {
    setErrorBloque("");
    setEdicion({ clave: null, fecha, inicio: "", fin: "", materiaInscritaId: opcionesMateria[0]?.value ?? "" });
  }

  function abrirEdicion(bloque) {
    setErrorBloque("");
    setEdicion({ ...bloque, materiaInscritaId: String(bloque.materiaInscritaId) });
  }

  function guardarBloque(evento) {
    evento.preventDefault();
    const materia = opcionesMateria.find((opcion) => opcion.value === String(edicion.materiaInscritaId));
    const candidato = {
      ...edicion,
      clave: edicion.clave ?? "nuevo",
      materiaInscritaId: Number(edicion.materiaInscritaId) || null,
      materia: materia?.label ?? "",
    };
    const error = validarBloque(candidato, borrador, desde);
    if (error) {
      setErrorBloque(error);
      return;
    }
    setBorrador((actual) =>
      edicion.clave
        ? actual.map((bloque) => (bloque.clave === edicion.clave ? { ...candidato } : bloque))
        : [...actual, nuevoBloque(candidato)]
    );
    setErrorGuardar("");
    setEdicion(null);
  }

  function eliminarBloque(clave) {
    setBorrador((actual) => actual.filter((bloque) => bloque.clave !== clave));
    setErrorGuardar("");
  }

  async function confirmar() {
    setGuardando(true);
    setErrorGuardar("");
    try {
      const guardado = await confirmarHorario(aSolicitud(desde, borrador));
      setConfirmado(guardado);
      setBorrador(null);
      toast.success("Horario de estudio confirmado.");
    } catch (error) {
      setErrorGuardar(error.message || "No se pudo guardar el horario.");
    } finally {
      setGuardando(false);
      setDialogo(null);
    }
  }

  // Descartar solo abandona la propuesta en pantalla: no se envía nada al servidor
  function descartar() {
    setBorrador(null);
    setDescartada(true);
    setErrorGuardar("");
    setDialogo(null);
  }

  if (carga.cargando) {
    return <EstadoVacio cargando mensaje="Generando la propuesta de horario..." />;
  }

  if (carga.error) {
    return (
      <Aviso tono="peligro" titulo="No se pudo cargar el horario de estudio">
        <p>{carga.error}</p>
        <Boton variante="texto" className="mt-2" onClick={recargar}>Reintentar</Boton>
      </Aviso>
    );
  }

  if (!sugerencia || sugerencia.dias.length === 0) {
    return (
      <EstadoVacio
        icono="fa-regular fa-calendar-xmark"
        mensaje={sugerencia?.mensaje || "No hay una propuesta de horario disponible."}
      />
    );
  }

  const textoSemana = `Semana del ${formatearFecha(desde)} al ${formatearFecha(sumarDias(desde, 6))}`;
  const advertencias = textosAdvertencias(sugerencia.advertencias);
  const bloquesOrdenados = borrador ? ordenarBloques(borrador) : [];
  const situacion = situacionPropuesta(sugerencia);
  const sinMaterias = situacion === SITUACION.SIN_MATERIAS;

  return (
    <div className="space-y-6">
      <EncabezadoModulo
        icono="fa-solid fa-calendar-check"
        descripcion={sinMaterias
          ? `${textoSemana}. Aún no hay una propuesta porque no tienes materias inscritas en el ciclo activo.`
          : `${textoSemana}. Revisa la propuesta, ajústala y confirma tu horario definitivo.`}
        acciones={
          <Boton variante="secundario" icono="fa-solid fa-rotate" onClick={pedirActualizacion} disabled={guardando}>
            Actualizar propuesta
          </Boton>
        }
      />

      {sinMaterias && (
        <Aviso tono="info" titulo="No tienes materias inscritas en el ciclo activo">
          <p>
            Para recibir una propuesta y armar tu horario de estudio, inscribe primero las materias del ciclo
            activo en Gestión Académica y registra sus actividades con el tiempo estimado.
          </p>
          {onNavegar && (
            <Boton variante="texto" className="mt-2" onClick={() => onNavegar("academico")}>
              Ir a Gestión Académica
            </Boton>
          )}
        </Aviso>
      )}

      {confirmado && !borrador && (
        <Tarjeta
          titulo="Resumen del horario confirmado"
          nivelTitulo={2}
          icono="fa-solid fa-circle-check"
          acciones={
            <Boton icono="fa-solid fa-file-pdf" onClick={() => window.print()}>
              Exportar a PDF
            </Boton>
          }
        >
          <p className="mb-4 text-sm text-ink-600">
            {textoSemana}. Confirmado el {textoConfirmadoEn(confirmado.confirmadoEn)}.
            Para guardarlo como PDF elige «Guardar como PDF» en el diálogo de impresión.
          </p>
          <TablaHorario horario={confirmado} />
          {!sinMaterias && (
            <div className="mt-4 flex flex-wrap gap-2 border-t border-line pt-4">
              <Boton variante="secundario" icono="fa-solid fa-pen" onClick={() => editarDesde(bloquesDesdeHorario(confirmado))}>
                Editar horario
              </Boton>
              <Boton variante="secundario" icono="fa-solid fa-wand-magic-sparkles" onClick={() => editarDesde(bloquesDesdeSugerencia(sugerencia))}>
                Partir de la propuesta actual
              </Boton>
            </div>
          )}
        </Tarjeta>
      )}

      {descartada && !borrador && !sinMaterias && (
        <Aviso tono="info" titulo="Propuesta descartada">
          <p>
            {confirmado
              ? "Se mantiene el horario que ya habías confirmado; los cambios no se guardaron."
              : "No se guardó ningún horario para esta semana."}
          </p>
          <Boton variante="texto" className="mt-2" onClick={() => editarDesde(bloquesDesdeSugerencia(sugerencia))}>
            Ver la propuesta otra vez
          </Boton>
        </Aviso>
      )}

      {borrador && !sinMaterias && (
        <>
          <Tarjeta titulo="Resumen de la propuesta" nivelTitulo={2} icono="fa-solid fa-chart-simple">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <Estadistica etiqueta="Tiempo recomendado" valor={formatearDuracion(sugerencia.resumen.minutosSugeridosSemanales)} />
              <Estadistica etiqueta="Sin programar" valor={formatearDuracion(sugerencia.resumen.minutosNoProgramadosSemanales)} />
              <Estadistica etiqueta="Materias consideradas" valor={sugerencia.resumen.materiasConsideradas} />
              <Estadistica etiqueta="En tu horario" valor={formatearDuracion(minutosTotales(borrador))} />
            </div>

            {advertencias.length > 0 && (
              <Aviso tono="advertencia" titulo="Advertencias" className="mt-4">
                <ul className="list-disc space-y-1 pl-5">
                  {advertencias.map((texto) => <li key={texto}>{texto}</li>)}
                </ul>
              </Aviso>
            )}

            {sugerencia.sinAsignar.length > 0 && (
              <Aviso tono="info" titulo="Materias con tiempo sin asignar" className="mt-4">
                <p>No hubo espacio libre suficiente para todo el tiempo recomendado:</p>
                <ul className="mt-1 list-disc space-y-1 pl-5">
                  {sugerencia.sinAsignar.map((materia) => (
                    <li key={materia.materiaInscritaId}>
                      {materia.materia}: faltan {formatearDuracion(Math.round(materia.horasSemanalesSinProgramar * 60))} de{" "}
                      {formatearDuracion(Math.round(materia.horasSemanalesRecomendadas * 60))} recomendadas.
                    </li>
                  ))}
                </ul>
              </Aviso>
            )}

            {situacion === SITUACION.SIN_ACTIVIDADES && (
              <Aviso tono="info" className="mt-4">
                No hay bloques sugeridos para esta semana porque tus materias del ciclo activo no tienen
                actividades pendientes con tiempo estimado. Puedes agregar bloques manualmente.
              </Aviso>
            )}
          </Tarjeta>

          <section aria-labelledby="titulo-propuesta" className="space-y-3">
            <h2 id="titulo-propuesta" className="text-lg font-semibold text-ink-900">Propuesta semanal</h2>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
              {dias.map((dia) => {
                const delDia = bloquesOrdenados.filter((bloque) => bloque.fecha === dia.fecha);
                return (
                  <div key={dia.fecha} className="flex flex-col rounded-xl border border-line bg-surface p-3 shadow-sm">
                    <div className="mb-2 border-b border-line pb-2">
                      <h3 className="font-semibold text-ink-900">{dia.nombre}</h3>
                      <p className="text-xs text-ink-600">
                        {formatearFecha(dia.fecha)} · {formatearDuracion(minutosTotales(delDia))}
                      </p>
                    </div>
                    <ul className="flex-1 space-y-2">
                      {delDia.length === 0 && <li className="text-xs text-ink-600">Sin bloques</li>}
                      {delDia.map((bloque) => (
                        <li key={bloque.clave} className="rounded-lg border-l-4 border-brand-700 bg-brand-50 p-2">
                          <p className="break-words text-sm font-semibold text-ink-900">{bloque.materia}</p>
                          <p className="text-xs text-ink-600">
                            {bloque.inicio} – {bloque.fin} · {formatearDuracion(minutosDe(bloque))}
                          </p>
                          <div className="mt-1 flex gap-1">
                            <Boton
                              variante="texto"
                              tamano="sm"
                              icono="fa-solid fa-pen"
                              aria-label={`Editar ${bloque.materia} de ${bloque.inicio} a ${bloque.fin}`}
                              onClick={() => abrirEdicion(bloque)}
                            >
                              Editar
                            </Boton>
                            <Boton
                              variante="texto"
                              tamano="sm"
                              icono="fa-solid fa-trash"
                              aria-label={`Quitar ${bloque.materia} de ${bloque.inicio} a ${bloque.fin}`}
                              onClick={() => eliminarBloque(bloque.clave)}
                              className="ml-2 text-danger-700"
                            >
                              Quitar
                            </Boton>
                          </div>
                        </li>
                      ))}
                    </ul>
                    <Boton
                      variante="secundario"
                      tamano="sm"
                      icono="fa-solid fa-plus"
                      className="mt-3 w-full"
                      onClick={() => abrirNuevo(dia.fecha)}
                      aria-label={`Agregar bloque el ${dia.nombre.toLowerCase()} ${formatearFecha(dia.fecha)}`}
                    >
                      Agregar
                    </Boton>
                  </div>
                );
              })}
            </div>
          </section>

          {errorGuardar && (
            <Aviso tono="peligro" titulo="No se pudo confirmar el horario">
              <p>{errorGuardar}</p>
            </Aviso>
          )}

          <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:flex-row sm:justify-end">
            <Boton variante="texto" onClick={() => editarDesde(bloquesDesdeSugerencia(sugerencia))} disabled={guardando}>
              Restablecer propuesta
            </Boton>
            <Boton variante="peligro" icono="fa-solid fa-xmark" onClick={() => setDialogo("descartar")} disabled={guardando}>
              Descartar
            </Boton>
            <Boton
              icono="fa-solid fa-check"
              onClick={() => setDialogo("confirmar")}
              disabled={guardando || borrador.length === 0}
            >
              Confirmar horario
            </Boton>
          </div>
        </>
      )}

      <Modal
        abierto={edicion !== null}
        titulo={edicion?.clave ? "Editar bloque de estudio" : "Agregar bloque de estudio"}
        onCerrar={() => setEdicion(null)}
        ancho="md"
      >
        {edicion && (
          <form className="space-y-4" onSubmit={guardarBloque} noValidate>
            <Selector
              label="Materia"
              options={opcionesMateria}
              value={String(edicion.materiaInscritaId)}
              onValorChange={(valor) => setEdicion((actual) => ({ ...actual, materiaInscritaId: valor }))}
            />
            <Selector
              label="Día"
              placeholder={null}
              options={dias.map((dia) => ({ value: dia.fecha, label: `${dia.nombre} ${formatearFecha(dia.fecha)}` }))}
              value={edicion.fecha}
              onValorChange={(valor) => setEdicion((actual) => ({ ...actual, fecha: valor }))}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Campo
                label="Hora de inicio"
                type="time"
                value={edicion.inicio}
                onValorChange={(valor) => setEdicion((actual) => ({ ...actual, inicio: valor }))}
              />
              <Campo
                label="Hora de fin"
                type="time"
                value={edicion.fin}
                onValorChange={(valor) => setEdicion((actual) => ({ ...actual, fin: valor }))}
              />
            </div>
            <p className="text-xs text-ink-600">
              Al confirmar se comprueba que el bloque no choque con tus clases, tu jornada laboral ni tu disponibilidad.
            </p>
            {errorBloque && <Aviso tono="peligro">{errorBloque}</Aviso>}
            <div className="flex flex-col-reverse gap-2 border-t border-line pt-4 sm:flex-row sm:justify-end">
              <Boton variante="secundario" onClick={() => setEdicion(null)}>Cancelar</Boton>
              <Boton type="submit">Guardar bloque</Boton>
            </div>
          </form>
        )}
      </Modal>

      <DialogoConfirmacion
        abierto={dialogo === "confirmar"}
        tono="normal"
        titulo="¿Confirmar el horario de estudio?"
        mensaje={`Se guardará como tu horario definitivo de la ${textoSemana.toLowerCase()} (${borrador?.length ?? 0} bloques, ${formatearDuracion(minutosTotales(borrador ?? []))}).${confirmado ? " Reemplazará el horario que ya habías confirmado." : ""}`}
        textoConfirmar={guardando ? "Guardando..." : "Confirmar"}
        procesando={guardando}
        onConfirmar={confirmar}
        onCancelar={() => setDialogo(null)}
      />

      <DialogoConfirmacion
        abierto={dialogo === "descartar"}
        titulo="¿Descartar la propuesta?"
        mensaje={confirmado
          ? "Se perderán los cambios y se mantendrá el horario que ya habías confirmado."
          : "No se guardará ningún horario para esta semana."}
        textoConfirmar="Descartar"
        onConfirmar={descartar}
        onCancelar={() => setDialogo(null)}
      />

      <DialogoConfirmacion
        abierto={dialogo === "actualizar"}
        titulo="¿Actualizar la propuesta?"
        mensaje="Tienes cambios sin guardar en tu horario. Si actualizas, se perderán y se cargará la propuesta nueva."
        textoConfirmar="Actualizar y descartar cambios"
        textoCancelar="Conservar mis cambios"
        onConfirmar={() => {
          setDialogo(null);
          recargar();
        }}
        onCancelar={() => setDialogo(null)}
      />

      {/* Hoja para imprimir: fuera de #root, que se oculta al imprimir */}
      {confirmado && createPortal(
        <div className="hoja-impresion hidden">
          <h1 className="text-xl font-bold">Horario de estudio</h1>
          <p className="mb-1 text-sm">{estudiante?.nombre}{estudiante?.carnet ? ` · ${estudiante.carnet}` : ""}</p>
          <p className="mb-4 text-sm">
            {textoSemana} · Confirmado el {textoConfirmadoEn(confirmado.confirmadoEn)} · Total {formatearDuracion(confirmado.minutosTotales)}
          </p>
          <TablaHorario horario={confirmado} />
        </div>,
        document.body
      )}
    </div>
  );
}

export default HorarioEstudio;
