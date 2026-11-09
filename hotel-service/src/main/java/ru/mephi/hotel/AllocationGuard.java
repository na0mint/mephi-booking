package ru.mephi.hotel;

import jakarta.persistence.*;
/** A database mutex serializes only short inventory mutations, never network calls. */
@Entity
@Table(name = "allocation_guard")
public class AllocationGuard {
    @Id Long id;
    protected AllocationGuard() {
    }
    AllocationGuard(long id) {
        this.id = id;
    }
}
