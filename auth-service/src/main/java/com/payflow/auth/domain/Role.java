package com.payflow.auth.domain;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
@Entity
@Table(name = "roles")
public class Role {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 64)
    private RoleName name;
    @Column(name = "description", length = 255)
    private String description;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 64)
    private Set<Permission> permissions = new LinkedHashSet<>();
    protected Role() {
    }
    public UUID getId() {
        return id;
    }
    public RoleName getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public Set<Permission> getPermissions() {
        return permissions;
    }
}
