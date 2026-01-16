# CDI Alternative Priority Demo

This Maven project demonstrates how CDI (Contexts and Dependency Injection) handles `@Alternative` beans with `@Priority` annotations.

## Overview

The project shows that when multiple `@Alternative` implementations of the same interface are present with different `@Priority` values, CDI selects the bean with the **highest** priority value (not the lowest).

## Project Structure

- **GreetingService** - Interface defining a simple greeting service
- **DefaultGreetingService** - Default implementation with `@ApplicationScoped` (not an alternative)
- **HighPriorityGreetingService** - Alternative implementation with `@Priority(100)`
- **LowPriorityGreetingService** - Alternative implementation with `@Priority(99)`

## Key Concepts

### @Alternative with @Priority

When using `@Alternative` with `@Priority`:
- Both alternatives are automatically enabled (no beans.xml configuration needed)
- CDI uses the priority value to resolve which bean to inject
- **Higher priority values win** (100 > 99)
- This is NOT ambiguous - CDI will select the highest priority alternative

### Test Results

The test `testHigherPriorityAlternativeIsSelected()` demonstrates that:
1. When `GreetingService` is injected, CDI considers all alternatives
2. `HighPriorityGreetingService` (Priority 100) wins over `LowPriorityGreetingService` (Priority 99)
3. The greeting message confirms the correct implementation was selected

## Building and Testing

```bash
# Build the project
mvn clean compile

# Run tests
mvn test
```

## Requirements

- Java 11 or higher
- Maven 3.6 or higher

## Dependencies

- **jakarta.enterprise.cdi-api** (4.0.1) - CDI API specification
- **weld-se-core** (5.1.0.Final) - Weld SE for standalone CDI testing
- **junit** (4.13.2) - Unit testing framework
