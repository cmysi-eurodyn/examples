package com.example.hexagonal.application.service;

import com.example.hexagonal.application.port.in.CreateTaskUseCase;
import com.example.hexagonal.application.port.in.ListTasksUseCase;
import com.example.hexagonal.application.port.out.TaskRepository;
import com.example.hexagonal.domain.Task;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Application service that orchestrates the two task use cases.
 *
 * <p>This class sits at the heart of the hexagon. It implements both inbound
 * ports ({@link CreateTaskUseCase} and {@link ListTasksUseCase}) and depends
 * only on the outbound port ({@link TaskRepository}) – never on a concrete
 * adapter.
 *
 * <ul>
 *   <li>It is annotated with {@code @ApplicationScoped} so CDI can inject it
 *       into inbound adapters.</li>
 *   <li>The {@link TaskRepository} is injected by CDI; at runtime the
 *       {@link com.example.hexagonal.adapter.out.persistence.InMemoryTaskRepository}
 *       implementation is used.</li>
 * </ul>
 */
@ApplicationScoped
public class TaskService implements CreateTaskUseCase, ListTasksUseCase {

    private final TaskRepository taskRepository;

    @Inject
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    public Task createTask(CreateTaskCommand command) {
        Task task = Task.create(command.title(), command.description());
        return taskRepository.save(task);
    }

    @Override
    public List<Task> listAllTasks() {
        return taskRepository.findAll();
    }
}
