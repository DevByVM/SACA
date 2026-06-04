package com.saca.api.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
public class GreetingController {

    // Opción A: Retornar texto plano
    // Endpoint: http://localhost:8080/hello
    @GetMapping("/hello")
    public String sayHelloText() {
        return "¡Hola Mundo desde mi API REST lupita!";
    }

    // Opción B: Retornar un JSON estructurado (Lo más común en APIs reales)
    // Endpoint: http://localhost:8080/hello/json o http://localhost:8080/hello/json?name=Lupe
    @GetMapping("/hello/json")
    public Map<String, String> sayHelloJson(@RequestParam(value = "name", defaultValue = "Mundo") String name) {
        Map<String, String> response = new HashMap<>();
        response.put("message", "¡Hola, " + name + "!");
        response.put("status", "success");
        return response; // Spring Boot lo transforma automáticamente a JSON usando Jackson
    }
}
