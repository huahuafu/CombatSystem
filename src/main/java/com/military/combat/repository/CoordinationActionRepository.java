package com.military.combat.repository;

import com.military.combat.entity.CoordinationAction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CoordinationActionRepository extends MongoRepository<CoordinationAction, String> {
    List<CoordinationAction> findBySide(String side);
    List<CoordinationAction> findByStatus(String status);
    List<CoordinationAction> findByType(String type);
}

