package com.example.hexagonal.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Core domain entity representing a Task.
 *
 * <p>This class belongs to the innermost layer of the hexagon. It has no
 * dependency on any framework, database, or transport technology.
 */
public class Task {

    private final String id;
    private final String title;
    private final String description;
    private TaskStatus status;
    private final Instant createdAt;

    public Task(String id, String title, String description, TaskStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Factory method – creates a new task with a generated ID and {@code OPEN} status. */
    public static Task create(String title, String description) {
        return new Task(UUID.randomUUID().toString(), title, description, TaskStatus.OPEN, Instant.now());
    }

    /** Marks the task as completed. */
    public void complete() {
        this.status = TaskStatus.COMPLETED;
    }

    // Getters

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task task)) return false;
        return id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
