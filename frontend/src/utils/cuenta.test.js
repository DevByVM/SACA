// Pruebas con el ejecutor de pruebas integrado en Node (npm test), sin dependencias adicionales
import { describe, test } from "node:test";
import assert from "node:assert/strict";

import {
  MENSAJE_CONTRASENIA_INVALIDA,
  MENSAJE_CONTRASENIA_LARGA,
  MENSAJE_IDENTIFICADOR_INVALIDO,
  MENSAJE_NOMBRE_INVALIDO,
  MENSAJE_NOMBRE_LARGO,
  MENSAJE_NOMBRE_OBLIGATORIO,
  bytesUtf8,
  correoDeCarnet,
  extraerTokenDelFragmento,
  quitarFragmento,
  validarContrasenia,
  validarIdentificador,
  validarNombre,
} from "./cuenta.js";

const TOKEN = "Ab3_-".repeat(8) + "xyz";

describe("enlace de activación", () => {
  test("lee el token del fragmento del enlace", () => {
    assert.equal(TOKEN.length, 43);
    assert.equal(extraerTokenDelFragmento(`#token=${TOKEN}`), TOKEN);
    assert.equal(extraerTokenDelFragmento(`token=${TOKEN}`), TOKEN);
  });

  test("ignora fragmentos sin token o con un token que no tiene el formato", () => {
    for (const fragmento of ["", "#", null, undefined, "#otra=1", "#token=", "#token=corto",
      `#token=${TOKEN}x`, `#token=${TOKEN.slice(1)}!`, `#token=${"a".repeat(42)}+`]) {
      assert.equal(extraerTokenDelFragmento(fragmento), null, String(fragmento));
    }
  });

  test("quita el fragmento sin añadir una entrada al historial", () => {
    const llamadas = [];
    const historial = { replaceState: (...argumentos) => llamadas.push(argumentos) };

    quitarFragmento({ hash: `#token=${TOKEN}`, pathname: "/", search: "?a=1" }, historial);
    quitarFragmento({ hash: "", pathname: "/", search: "" }, historial);

    assert.deepEqual(llamadas, [[null, "", "/?a=1"]]);
  });
});

describe("contraseña", () => {
  test("exige 8 caracteres, una mayúscula y un número", () => {
    assert.equal(validarContrasenia("Segura123"), null);
    for (const contrasenia of ["", null, "Corta1", "sinmayuscula1", "SINNUMEROS"]) {
      assert.equal(validarContrasenia(contrasenia), MENSAJE_CONTRASENIA_INVALIDA, String(contrasenia));
    }
  });

  test("admite hasta 72 bytes UTF-8, no 72 caracteres", () => {
    const ascii72 = "A1" + "a".repeat(70);
    const enie72 = "A1" + "ñ".repeat(35);
    const emoji72 = "A1" + "a".repeat(66) + "😀";

    for (const contrasenia of [ascii72, enie72, emoji72]) {
      assert.equal(bytesUtf8(contrasenia), 72);
      assert.equal(validarContrasenia(contrasenia), null);
    }
    assert.equal(enie72.length, 37);
    for (const contrasenia of [ascii72 + "a", enie72 + "ñ", emoji72 + "a"]) {
      assert.equal(validarContrasenia(contrasenia), MENSAJE_CONTRASENIA_LARGA);
    }
  });
});

describe("nombre", () => {
  test("acepta letras, tildes, ñ y espacios simples", () => {
    assert.equal(validarNombre("  Ana María Peña "), null);
  });

  test("rechaza vacíos, otros caracteres y más de 120 caracteres", () => {
    assert.equal(validarNombre("   "), MENSAJE_NOMBRE_OBLIGATORIO);
    assert.equal(validarNombre(null), MENSAJE_NOMBRE_OBLIGATORIO);
    assert.equal(validarNombre("Nombre 123"), MENSAJE_NOMBRE_INVALIDO);
    assert.equal(validarNombre("Dos  espacios"), MENSAJE_NOMBRE_INVALIDO);
    assert.equal(validarNombre("A".repeat(121)), MENSAJE_NOMBRE_LARGO);
  });
});

describe("identificador del reenvío", () => {
  test("acepta el carnet o el correo institucional derivado de él", () => {
    for (const identificador of ["AP23001", " ap23001 ", "ap23001@ues.edu.sv", " AP23001@UES.EDU.SV "]) {
      assert.equal(validarIdentificador(identificador), null, identificador);
    }
    assert.equal(correoDeCarnet(" ap23001 "), "ap23001@ues.edu.sv");
  });

  test("rechaza otros correos, subdominios, espacios y carnets mal formados", () => {
    for (const identificador of ["", "   ", null, "AP2300", "ABC12345", "ap23001@gmail.com",
      "ap23001@ues.edu.sv.otro.com", "x.ap23001@ues.edu.sv", "ap23001 @ues.edu.sv", "@ues.edu.sv",
      "ap23001@estudiantes.ues.edu.sv"]) {
      assert.equal(validarIdentificador(identificador), MENSAJE_IDENTIFICADOR_INVALIDO, String(identificador));
    }
  });
});
