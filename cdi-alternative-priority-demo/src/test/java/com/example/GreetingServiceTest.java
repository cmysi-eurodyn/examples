package com.example;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test that demonstrates how CDI resolves multiple @Alternative beans
 * with different @Priority values.
 * 
 * When multiple alternatives with @Priority are present, CDI selects the one
 * with the HIGHEST priority value (not lowest). In this case, Priority 100
 * wins over Priority 99.
 */
public class GreetingServiceTest {
    
    private WeldContainer container;
    
    @After
    public void cleanup() {
        if (container != null) {
            container.close();
        }
    }
    
    /**
     * This test demonstrates that when multiple @Alternative implementations
     * with @Priority are present, CDI selects the one with the highest priority value.
     * 
     * HighPriorityGreetingService has Priority 100 and LowPriorityGreetingService 
     * has Priority 99, so the HighPriorityGreetingService should be selected.
     */
    @Test
    public void testHigherPriorityAlternativeIsSelected() {
        // Initialize Weld container
        Weld weld = new Weld();
        container = weld.initialize();
        
        // Get GreetingService - should resolve to HighPriorityGreetingService (Priority 100)
        GreetingService service = container.select(GreetingService.class).get();
        
        // Verify that the highest priority alternative was selected
        String greeting = service.greet("World");
        assertEquals("Greetings, World! (Priority 100)", greeting);
    }
}
