package com.example;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/**
 * Alternative implementation with priority 99.
 * This alternative has lower priority than HighPriorityGreetingService
 * and will not be selected when both alternatives are present.
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
