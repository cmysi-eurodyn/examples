package com.example;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Alternative implementation with priority 99.
 * When multiple alternatives are enabled, this creates an ambiguous dependency.
 */
@Alternative
@Priority(99)
@ApplicationScoped
public class LowPriorityGreetingService implements GreetingService {
    
    @Override
    public String greet(String name) {
        return "Hi, " + name + "! (Priority 99)";
    }
}
