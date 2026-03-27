package com.example.hexagonal.application.port.in;

import com.example.hexagonal.domain.Task;

/**
 * Inbound port – Use Case 1: Create a new Task.
 *
 * <p>Defines the contract that inbound adapters (e.g., REST, CLI) must call
 * when they want to create a task. The application service implements this
 * interface, keeping adapters decoupled from the service class.
 */
public interface CreateTaskUseCase {

    /**
     * Creates a new task and persists it.
     *
     * @param command the details needed to create the task
     * @return the newly created {@link Task}
     */
    Task createTask(CreateTaskCommand command);

    /** Value object carrying the input data for this use case. */
    record CreateTaskCommand(String title, String description) {
        public CreateTaskCommand {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
        }
    }
}
