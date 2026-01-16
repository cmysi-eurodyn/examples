package com.example;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Alternative implementation with priority 100.
 * When multiple alternatives are enabled, this creates an ambiguous dependency.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class HighPriorityGreetingService implements GreetingService {
    
    @Override
    public String greet(String name) {
        return "Greetings, " + name + "! (Priority 100)";
    }
}
