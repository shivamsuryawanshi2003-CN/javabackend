package com.jobra.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class InternalRoleUpdateRequest {

    @NotBlank
    @Email
    private String email;

    /** One of: END_USER, ADMIN, SUPER_ADMIN */
    @NotBlank
    private String role;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
