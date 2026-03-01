package com.example.hexagonal.adapter.in.rest;

import com.example.hexagonal.adapter.in.rest.dto.CreateTaskRequest;
import com.example.hexagonal.adapter.in.rest.dto.TaskResponse;
import com.example.hexagonal.application.port.in.CreateTaskUseCase;
import com.example.hexagonal.application.port.in.CreateTaskUseCase.CreateTaskCommand;
import com.example.hexagonal.application.port.in.ListTasksUseCase;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Inbound REST adapter that exposes the two task use cases over HTTP.
 *
 * <p>This class is intentionally thin: it translates HTTP requests into use-
 * case commands, delegates to the inbound ports, and maps domain results back
 * to HTTP responses. No business logic lives here.
 */
@Path("/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {

    private final CreateTaskUseCase createTaskUseCase;
    private final ListTasksUseCase listTasksUseCase;

    @Inject
    public TaskResource(CreateTaskUseCase createTaskUseCase, ListTasksUseCase listTasksUseCase) {
        this.createTaskUseCase = createTaskUseCase;
        this.listTasksUseCase = listTasksUseCase;
    }

    /**
     * Use Case 1 – Create a Task.
     *
     * <p>POST /tasks
     * <p>Validates the request body, builds a {@link CreateTaskCommand}, and
     * delegates to the {@link CreateTaskUseCase} port.
     *
     * @param request the task creation request
     * @return {@code 201 Created} with the created task representation
     */
    @POST
    public Response createTask(@Valid CreateTaskRequest request) {
        CreateTaskCommand command = new CreateTaskCommand(request.title, request.description);
        TaskResponse response = TaskResponse.from(createTaskUseCase.createTask(command));
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    /**
     * Use Case 2 – List all Tasks.
     *
     * <p>GET /tasks
     * <p>Delegates to the {@link ListTasksUseCase} port and maps every domain
     * {@link com.example.hexagonal.domain.Task} to a {@link TaskResponse} DTO.
     *
     * @return {@code 200 OK} with a list of task representations
     */
    @GET
    public List<TaskResponse> listTasks() {
        return listTasksUseCase.listAllTasks()
                .stream()
                .map(TaskResponse::from)
                .toList();
    }
}
