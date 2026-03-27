package com.example.hexagonal.application.port.out;

import com.example.hexagonal.domain.Task;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port – persistence contract for {@link Task} entities.
 *
 * <p>The application core defines this interface; infrastructure adapters
 * (e.g., in-memory map, JPA, external service) implement it. This way the
 * domain and use-case logic never depends on a concrete data store.
 */
public interface TaskRepository {

    /**
     * Persists a new task or updates an existing one.
     *
     * @param task the task to save
     * @return the saved task
     */
    Task save(Task task);

    /**
     * Finds a task by its unique identifier.
     *
     * @param id the task ID
     * @return an {@link Optional} containing the task, or empty if not found
     */
    Optional<Task> findById(String id);

    /**
     * Returns all stored tasks.
     *
     * @return an unmodifiable list of all tasks
     */
    List<Task> findAll();
}
