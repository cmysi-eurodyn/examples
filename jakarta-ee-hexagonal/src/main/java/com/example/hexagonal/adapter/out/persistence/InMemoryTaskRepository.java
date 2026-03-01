package com.example.hexagonal.adapter.out.persistence;

import com.example.hexagonal.application.port.out.TaskRepository;
import com.example.hexagonal.domain.Task;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound adapter – in-memory implementation of {@link TaskRepository}.
 *
 * <p>Uses a {@link LinkedHashMap} to preserve insertion order so that
 * {@link #findAll()} returns tasks in the order they were created.
 *
 * <p>This class is the only component that knows how tasks are stored. Swap it
 * out (e.g., with a JPA adapter) without touching the application core.
 */
@ApplicationScoped
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<String, Task> store = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public Task save(Task task) {
        store.put(task.getId(), task);
        return task;
    }

    @Override
    public Optional<Task> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Task> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }
}
