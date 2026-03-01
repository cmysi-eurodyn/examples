package com.example.hexagonal.application.port.in;

import com.example.hexagonal.domain.Task;

import java.util.List;

/**
 * Inbound port – Use Case 2: List all Tasks.
 *
 * <p>Inbound adapters call this port to retrieve all persisted tasks without
 * depending on the concrete application service or any persistence technology.
 */
public interface ListTasksUseCase {

    /**
     * Returns all tasks currently stored in the system.
     *
     * @return an unmodifiable list of tasks (may be empty, never {@code null})
     */
    List<Task> listAllTasks();
}
