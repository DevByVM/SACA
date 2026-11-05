package com.saca.api.service;

import com.saca.api.service.IntervalosMinutos.Intervalo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

// Unión, recorte e intersección de intervalos semiabiertos en minutos
class IntervalosMinutosTest {

    private static Intervalo i(int inicioHora, int inicioMinuto, int finHora, int finMinuto) {
        return new Intervalo(inicioHora * 60 + inicioMinuto, finHora * 60 + finMinuto);
    }

    @Test
    void unirFusionaLosSolapadosYConservaLosSeparados() {
        assertThat(IntervalosMinutos.unir(List.of(i(10, 0, 12, 0), i(8, 0, 11, 0), i(14, 0, 15, 0))))
                .containsExactly(i(8, 0, 12, 0), i(14, 0, 15, 0));
    }

    @Test
    void unirFusionaUnIntervaloContenidoEnOtro() {
        assertThat(IntervalosMinutos.unir(List.of(i(8, 0, 16, 0), i(9, 0, 10, 0)))).containsExactly(i(8, 0, 16, 0));
    }

    @Test
    void losIntervalosQueSoloSeTocanNoSumanNiRestanMinutos() {
        List<Intervalo> consecutivos = List.of(i(8, 0, 10, 0), i(10, 0, 12, 0));

        assertThat(IntervalosMinutos.duracion(consecutivos)).isEqualTo(240);
        assertThat(IntervalosMinutos.interseccion(List.of(i(8, 0, 10, 0)), List.of(i(10, 0, 12, 0)))).isEmpty();
    }

    @Test
    void losIntervalosVaciosOInvertidosSeDescartan() {
        assertThat(IntervalosMinutos.unir(List.of(i(9, 0, 9, 0), i(12, 0, 8, 0)))).isEmpty();
        assertThat(IntervalosMinutos.duracion(List.of())).isZero();
    }

    @Test
    void laDuracionDeIntervalosDuplicadosNoSeDuplica() {
        assertThat(IntervalosMinutos.duracion(List.of(i(8, 0, 9, 0), i(8, 0, 9, 0), i(8, 30, 9, 0)))).isEqualTo(60);
    }

    @Test
    void recortarLimitaALaVentana() {
        assertThat(IntervalosMinutos.recortar(List.of(i(8, 0, 12, 0), i(13, 0, 14, 0)), 10 * 60, 13 * 60 + 30))
                .containsExactly(i(10, 0, 12, 0), i(13, 0, 13, 30));
        assertThat(IntervalosMinutos.recortar(List.of(i(8, 0, 9, 0)), 10 * 60, 12 * 60)).isEmpty();
    }

    @Test
    void laInterseccionUneAntesDeCruzarParaNoContarDosVeces() {
        List<Intervalo> reservado = List.of(i(8, 0, 12, 0));
        List<Intervalo> restricciones = List.of(i(9, 0, 10, 0), i(9, 30, 11, 0));

        List<Intervalo> comunes = IntervalosMinutos.interseccion(reservado, restricciones);

        assertThat(comunes).containsExactly(i(9, 0, 11, 0));
        assertThat(IntervalosMinutos.duracion(comunes)).isEqualTo(120);
    }

    @Test
    void laInterseccionConVariosTramosDevuelveCadaTramo() {
        assertThat(IntervalosMinutos.interseccion(List.of(i(8, 0, 14, 0)), List.of(i(7, 0, 9, 0), i(12, 0, 13, 0), i(15, 0, 16, 0))))
                .containsExactly(i(8, 0, 9, 0), i(12, 0, 13, 0));
    }

    @Test
    void elResultadoNoDependeDelOrdenDeEntrada() {
        List<Intervalo> intervalos = new ArrayList<>(List.of(
                i(8, 0, 9, 0), i(8, 30, 10, 0), i(13, 0, 14, 0), i(13, 30, 13, 45), i(18, 0, 20, 0), i(19, 0, 21, 0)));
        List<Intervalo> esperado = IntervalosMinutos.unir(intervalos);

        Random aleatorio = new Random(32);
        for (int k = 0; k < 20; k++) {
            Collections.shuffle(intervalos, aleatorio);
            assertThat(IntervalosMinutos.unir(intervalos)).isEqualTo(esperado);
        }
        assertThat(esperado).containsExactly(i(8, 0, 10, 0), i(13, 0, 14, 0), i(18, 0, 21, 0));
    }
}
