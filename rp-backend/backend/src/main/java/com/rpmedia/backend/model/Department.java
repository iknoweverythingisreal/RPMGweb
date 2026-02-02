package com.rpmedia.backend.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "departments")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // === One-to-many กับ User ===
    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToMany(mappedBy = "department")
    private List<User> users;

    // === Getters & Setters ===

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }
}
