package com.example.hexagonal.adapter.in.rest.dto;

import com.example.hexagonal.domain.Task;
import com.example.hexagonal.domain.TaskStatus;

import java.time.Instant;

/**
 * DTO for task data returned in REST responses.
 *
 * <p>Converts a domain {@link Task} into a simple, serialisable record so the
 * REST adapter never exposes internal domain objects directly.
 */
public record TaskResponse(
        String id,
        String title,
        String description,
        TaskStatus status,
        Instant createdAt) {

    /** Factory method – builds a {@link TaskResponse} from a domain {@link Task}. */
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt());
    }
}
