package com.example;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default implementation of GreetingService.
 * This is the standard implementation used when no alternatives are enabled.
 */
@ApplicationScoped
public class DefaultGreetingService implements GreetingService {
    
    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
