package com.example;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Alternative implementation with priority 100.
 * This alternative has the highest priority and will be selected by CDI
 * when multiple alternatives with @Priority are present.
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
