package com.example.hexagonal.application.service;

import com.example.hexagonal.application.port.in.CreateTaskUseCase.CreateTaskCommand;
import com.example.hexagonal.application.port.out.TaskRepository;
import com.example.hexagonal.domain.Task;
import com.example.hexagonal.domain.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TaskService}.
 *
 * <p>These tests live in the application layer and use a hand-written stub for
 * the {@link TaskRepository} outbound port, so no framework (Quarkus, CDI) is
 * started. They run fast and validate pure business logic.
 */
class TaskServiceTest {

    private StubTaskRepository repository;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repository = new StubTaskRepository();
        service = new TaskService(repository);
    }

    // -------------------------------------------------------------------------
    // Use Case 1: Create Task
    // -------------------------------------------------------------------------

    @Test
    void createTask_withValidCommand_persistsAndReturnsTask() {
        CreateTaskCommand command = new CreateTaskCommand("Buy groceries", "Milk, eggs, bread");

        Task created = service.createTask(command);

        assertThat(created.getId()).isNotBlank();
        assertThat(created.getTitle()).isEqualTo("Buy groceries");
        assertThat(created.getDescription()).isEqualTo("Milk, eggs, bread");
        assertThat(created.getStatus()).isEqualTo(TaskStatus.OPEN);
        assertThat(created.getCreatedAt()).isNotNull();
    }

    @Test
    void createTask_persistsInRepository() {
        CreateTaskCommand command = new CreateTaskCommand("Book tickets", null);

        Task created = service.createTask(command);

        assertThat(repository.findById(created.getId())).isPresent();
    }

    @Test
    void createTask_withBlankTitle_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new CreateTaskCommand("  ", "some description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    @Test
    void createTask_withNullTitle_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new CreateTaskCommand(null, "some description"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
    }

    // -------------------------------------------------------------------------
    // Use Case 2: List Tasks
    // -------------------------------------------------------------------------

    @Test
    void listAllTasks_whenEmpty_returnsEmptyList() {
        List<Task> tasks = service.listAllTasks();

        assertThat(tasks).isEmpty();
    }

    @Test
    void listAllTasks_returnsAllPersistedTasks() {
        service.createTask(new CreateTaskCommand("Task A", null));
        service.createTask(new CreateTaskCommand("Task B", "Some details"));

        List<Task> tasks = service.listAllTasks();

        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(Task::getTitle).containsExactlyInAnyOrder("Task A", "Task B");
    }

    @Test
    void listAllTasks_returnedListIsUnmodifiable() {
        service.createTask(new CreateTaskCommand("Task X", null));

        List<Task> tasks = service.listAllTasks();

        assertThatThrownBy(() -> tasks.add(Task.create("extra", null)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // -------------------------------------------------------------------------
    // Stub – in-memory task repository used only in tests
    // -------------------------------------------------------------------------

    static class StubTaskRepository implements TaskRepository {

        private final Map<String, Task> store = new LinkedHashMap<>();

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
}
