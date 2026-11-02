-- MySQL dump 10.13  Distrib 8.4.9, for Linux (x86_64)
--
-- Host: localhost    Database: saca_db
-- ------------------------------------------------------
-- Server version	8.4.9

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `actividades_academicas`
--

DROP TABLE IF EXISTS `actividades_academicas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `actividades_academicas` (
  `id_actividad` bigint NOT NULL AUTO_INCREMENT,
  `estado` varchar(255) DEFAULT NULL,
  `fecha_completada` datetime(6) DEFAULT NULL,
  `fecha_entrega` datetime(6) DEFAULT NULL,
  `fecha_inicio` datetime(6) DEFAULT NULL,
  `nombre` varchar(255) DEFAULT NULL,
  `porcentaje_evaluacion` double NOT NULL,
  `tiempo_estimado_horas` double NOT NULL,
  `tipo_actividad` enum('INVESTIGACION','LABORATORIO','PARCIAL','PROYECTO') NOT NULL,
  `materia_inscrita_id` bigint NOT NULL,
  `nota_id` int DEFAULT NULL,
  PRIMARY KEY (`id_actividad`),
  UNIQUE KEY `UKc7hqgm4cprroon1y3mp9tayq7` (`nota_id`),
  KEY `FK2xwold05y82r7uageb432837n` (`materia_inscrita_id`),
  CONSTRAINT `FK2xwold05y82r7uageb432837n` FOREIGN KEY (`materia_inscrita_id`) REFERENCES `materias_inscritas` (`id`),
  CONSTRAINT `FK7w2aflw217chci8iel968skd8` FOREIGN KEY (`nota_id`) REFERENCES `notas` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ciclos_academicos`
--

DROP TABLE IF EXISTS `ciclos_academicos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ciclos_academicos` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anio` int NOT NULL,
  `estado` enum('ACTIVO','FINALIZADO','PLANIFICADO') NOT NULL,
  `fecha_fin` date NOT NULL,
  `fecha_inicio` date NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `estudiante_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoiipk43sr2gq82kns3hr7v8n9` (`estudiante_id`),
  CONSTRAINT `FKoiipk43sr2gq82kns3hr7v8n9` FOREIGN KEY (`estudiante_id`) REFERENCES `estudiantes` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `disponibilidades_semanales`
--

DROP TABLE IF EXISTS `disponibilidades_semanales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `disponibilidades_semanales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ciclo_id` bigint NOT NULL,
  `dia_semana` varchar(15) NOT NULL,
  `estudiante_id` bigint NOT NULL,
  `hora_fin` time(6) NOT NULL,
  `hora_inicio` time(6) NOT NULL,
  `tipo_bloque` varchar(30) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `estudiantes`
--

DROP TABLE IF EXISTS `estudiantes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `estudiantes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bloqueado_hasta` datetime(6) DEFAULT NULL,
  `carnet` varchar(20) DEFAULT NULL,
  `contrasenia` varchar(255) NOT NULL,
  `correo_institucional` varchar(120) DEFAULT NULL,
  `cuenta_activa` bit(1) DEFAULT NULL,
  `intentos_fallidos` int DEFAULT NULL,
  `nombre` varchar(120) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKde5xi3q0e0tvsmhipq5pdrbv1` (`carnet`),
  UNIQUE KEY `UK36jwykef178ktmiawwpyrnasb` (`correo_institucional`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `horarios_clase`
--

DROP TABLE IF EXISTS `horarios_clase`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `horarios_clase` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dia_semana` enum('DOMINGO','JUEVES','LUNES','MARTES','MIERCOLES','SABADO','VIERNES') NOT NULL,
  `docente_tutor` varchar(120) DEFAULT NULL,
  `hora_fin` time(6) NOT NULL,
  `hora_inicio` time(6) NOT NULL,
  `modalidad` enum('HIBRIDA','PRESENCIAL','VIRTUAL') NOT NULL,
  `materia_inscrita_id` bigint NOT NULL,
  `actividad_academica_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc1dciwfiwqsb1bwiym0e2pl7f` (`materia_inscrita_id`),
  KEY `FKe302umf9hfwf7ffvneml5bl7y` (`actividad_academica_id`),
  CONSTRAINT `FKc1dciwfiwqsb1bwiym0e2pl7f` FOREIGN KEY (`materia_inscrita_id`) REFERENCES `materias_inscritas` (`id`),
  CONSTRAINT `FKe302umf9hfwf7ffvneml5bl7y` FOREIGN KEY (`actividad_academica_id`) REFERENCES `actividades_academicas` (`id_actividad`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `jornadas_laborales`
--

DROP TABLE IF EXISTS `jornadas_laborales`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `jornadas_laborales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dia_semana` varchar(15) NOT NULL,
  `estudiante_id` bigint NOT NULL,
  `hora_fin` time(6) NOT NULL,
  `hora_inicio` time(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `materias_inscritas`
--

DROP TABLE IF EXISTS `materias_inscritas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `materias_inscritas` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `codigo` varchar(30) NOT NULL,
  `grupo_teorico` varchar(30) NOT NULL,
  `nombre` varchar(120) NOT NULL,
  `ciclo_academico_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt13cf3sel9adakxi9rcs392o` (`ciclo_academico_id`),
  CONSTRAINT `FKt13cf3sel9adakxi9rcs392o` FOREIGN KEY (`ciclo_academico_id`) REFERENCES `ciclos_academicos` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notas`
--

DROP TABLE IF EXISTS `notas`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notas` (
  `id` int NOT NULL AUTO_INCREMENT,
  `fecha_registro` date DEFAULT NULL,
  `valor_obtenido` double NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `perfil_academico`
--

DROP TABLE IF EXISTS `perfil_academico`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `perfil_academico` (
  `id_perfil` bigint NOT NULL AUTO_INCREMENT,
  `carrera` varchar(120) DEFAULT NULL,
  `facultad` varchar(120) DEFAULT NULL,
  `fecha_ingreso` date DEFAULT NULL,
  `plan_estudio` varchar(50) DEFAULT NULL,
  `estudiante_id` bigint NOT NULL,
  PRIMARY KEY (`id_perfil`),
  UNIQUE KEY `UKfh8nfgta7oc35cnqifigxlfyj` (`estudiante_id`),
  CONSTRAINT `FKft85b4wyigjt8lencv5t29mvb` FOREIGN KEY (`estudiante_id`) REFERENCES `estudiantes` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `tipo_actividad`
--

DROP TABLE IF EXISTS `tipo_actividad`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tipo_actividad` (
  `id_tipo_actividad` int NOT NULL AUTO_INCREMENT,
  `activo` bit(1) NOT NULL,
  `nombre` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id_tipo_actividad`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-06-09 21:34:46
