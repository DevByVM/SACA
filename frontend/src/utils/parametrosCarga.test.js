import { test } from "node:test";
import assert from "node:assert/strict";
import { validarUmbrales, obtenerEscalaHoras, porcentajeHoras, describirUmbrales } from "./parametrosCarga.js";

test("acepta valores positivos ordenados, incluidos decimales", () => {
  assert.equal(validarUmbrales(20, 35), null);
  assert.equal(validarUmbrales("12.5", "28.75"), null);
  assert.equal(validarUmbrales("0.01", "0.02"), null);
});

test("rechaza vacíos, valores no finitos, cero y negativos", () => {
  for (const valor of ["", " ", null, undefined, "texto", NaN, Infinity, -Infinity, 0, -1]) {
    assert.ok(validarUmbrales(valor, 35));
    assert.ok(validarUmbrales(20, valor));
  }
});

test("rechaza umbrales iguales o invertidos", () => {
  assert.ok(validarUmbrales(20, 20));
  assert.ok(validarUmbrales(36, 35));
});

test("la escala y las barras siguen el umbral recibido, por encima o por debajo de 40 horas", () => {
  assert.equal(obtenerEscalaHoras({ umbralCritica: 35 }), 40);
  assert.equal(obtenerEscalaHoras({ umbralCritica: 70 }), 80);
  assert.equal(porcentajeHoras(40, { umbralCritica: 70 }), 50);
  assert.equal(porcentajeHoras(4, { umbralCritica: 7 }), 50);
  assert.equal(porcentajeHoras(100, { umbralCritica: 7 }), 100);
  assert.equal(obtenerEscalaHoras(null), null);
  assert.equal(porcentajeHoras(10, null), 0);
  assert.ok(Number.isFinite(obtenerEscalaHoras({ umbralCritica: Number.MAX_VALUE })));
});

test("los textos muestran los dos umbrales recibidos y los límites inclusivos", () => {
  assert.equal(describirUmbrales({ umbralElevada: 12.5, umbralCritica: 28.75 }),
    "Normal hasta 12.5 hrs, elevada con más de 12.5 y hasta 28.75 hrs, y crítica con más de 28.75 hrs.");
});
