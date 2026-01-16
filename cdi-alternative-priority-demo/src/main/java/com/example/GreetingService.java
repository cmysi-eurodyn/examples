package com.example;

/**
 * Service interface for greeting operations.
 */
public interface GreetingService {
    /**
     * Generate a greeting message for the given name.
     * 
     * @param name the name to greet
     * @return the greeting message
     */
    String greet(String name);
}
