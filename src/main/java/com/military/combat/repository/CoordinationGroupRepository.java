package com.military.combat.repository;

import com.military.combat.entity.CoordinationGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CoordinationGroupRepository extends MongoRepository<CoordinationGroup, String> {
    List<CoordinationGroup> findBySide(String side);
    List<CoordinationGroup> findByStatus(String status);
    List<CoordinationGroup> findBySideAndStatus(String side, String status);
}

