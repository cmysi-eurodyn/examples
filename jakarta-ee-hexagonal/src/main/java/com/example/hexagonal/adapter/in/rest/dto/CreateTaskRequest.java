package com.example.hexagonal.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for the "create task" REST request body.
 *
 * <p>Bean Validation annotations enforce basic input constraints at the
 * adapter boundary before data enters the application core.
 */
public class CreateTaskRequest {

    @NotBlank(message = "title must not be blank")
    @Size(max = 255, message = "title must not exceed 255 characters")
    public String title;

    @Size(max = 1024, message = "description must not exceed 1024 characters")
    public String description;

    // Default constructor required for JSON deserialization
    public CreateTaskRequest() {}

    public CreateTaskRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
