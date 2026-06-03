import React, { useState, useEffect } from "react";
import axios from "axios";

function ClimaActual() {
  // Estados para almacenar el clima, la carga y los errores
  const [clima, setClima] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState(null);

  // Coordenadas de ejemplo (Madrid, España). Puedes cambiarlas por las de tu ciudad.
  const latitud = 40.4165;
  const longitud = -3.7026;

  useEffect(() => {
    const obtenerClima = async () => {
      try {
        // Hacemos la petición a la API de Open-Meteo
        const url = `https://api.open-meteo.com/v1/forecast?latitude=${latitud}&longitude=${longitud}&current_weather=true`;
        const respuesta = await axios.get(url);

        // Guardamos los datos del clima actual en el estado
        setClima(respuesta.data.current_weather);
        setCargando(false);
      } catch (err) {
        setError("No se pudo obtener el clima. Inténtalo de nuevo.");
        setCargando(false);
      }
    };

    obtenerClima();
  }, []); // Array vacío para que solo se ejecute al montar el componente

  // Renderizado condicional
  if (cargando) return <p style={styles.texto}>Cargando el clima actual...</p>;
  if (error) return <p style={styles.error}>{error}</p>;

  return (
    <div style={styles.tarjeta}>
      <h2 style={styles.titulo}>Clima Actual</h2>
      <p style={styles.subtitulo}>
        Coordenadas: {latitud}, {longitud}
      </p>
      <hr style={styles.linea} />

      <div style={styles.infoContenedor}>
        <div style={styles.temperatura}>{clima.temperature}°C</div>
        <div style={styles.detalles}>
          <p>💨 **Viento:** {clima.windspeed} km/h</p>
          <p>🧭 **Dirección:** {clima.winddirection}°</p>
          <p>🕒 **Hora API:** {clima.time.split("T")[1]}</p>
        </div>
      </div>
    </div>
  );
}

// Unos estilos rápidos en línea para que se vea genial desde el principio
const styles = {
  tarjeta: {
    background: "linear-gradient(135deg, #74b9ff, #0984e3)",
    color: "#fff",
    padding: "24px",
    borderRadius: "16px",
    width: "300px",
    margin: "20px auto",
    boxShadow: "0 8px 16px rgba(0,0,0,0.15)",
    fontFamily: "Arial, sans-serif",
  },
  titulo: { margin: "0 0 4px 0", fontSize: "24px" },
  subtitulo: { margin: "0", fontSize: "12px", opacity: 0.8 },
  linea: {
    border: "none",
    borderTop: "1px solid rgba(255,255,255,0.3)",
    margin: "16px 0",
  },
  infoContenedor: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
  },
  temperatura: { fontSize: "48px", fontWeight: "bold" },
  detalles: { fontSize: "14px", textAlign: "left" },
  texto: { textAlign: "center", fontFamily: "Arial, sans-serif" },
  error: {
    color: "#ff7675",
    textAlign: "center",
    fontFamily: "Arial, sans-serif",
    fontWeight: "bold",
  },
};

export default ClimaActual;
