package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.Weapon;
import com.military.combat.repository.CombatUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombatUnitServiceTest {

    @Mock
    private CombatUnitRepository repository;

    @Mock
    private ScenarioService scenarioService;

    @InjectMocks
    private CombatUnitService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testAddUnit() {
        CombatUnit unit = new CombatUnit();
        unit.setName("Test Unit");
        unit.setSide("RED");
        unit.setType("TANK");
        unit.setCombatPower(100);
        unit.setMaxPower(100);
        unit.setAttackRange(20);
        unit.setSpeed(10);

        when(repository.save(unit)).thenReturn(unit);

        CombatUnit result = service.addUnit(unit);

        assertNotNull(result);
        assertEquals("Test Unit", result.getName());
        assertEquals("RED", result.getSide());
        assertEquals("TANK", result.getType());
        verify(repository, times(1)).save(unit);
    }

    @Test
    void testUpdateUnit() {
        CombatUnit existingUnit = new CombatUnit();
        existingUnit.setId("1");
        existingUnit.setName("Old Unit");
        existingUnit.setCombatPower(50);
        existingUnit.setMaxPower(100);

        CombatUnit updatedUnit = new CombatUnit();
        updatedUnit.setId("1");
        updatedUnit.setName("Updated Unit");
        updatedUnit.setCombatPower(75);
        updatedUnit.setMaxPower(100);

        when(repository.findById("1")).thenReturn(Optional.of(existingUnit));
        when(repository.save(existingUnit)).thenReturn(existingUnit);

        CombatUnit result = service.updateUnit(updatedUnit);

        assertNotNull(result);
        assertEquals("Updated Unit", result.getName());
        assertEquals(75, result.getCombatPower());
        verify(repository, times(1)).findById("1");
        verify(repository, times(1)).save(existingUnit);
    }

    @Test
    void testDeleteUnit() {
        when(repository.existsById("1")).thenReturn(true);
        doNothing().when(repository).deleteById("1");

        assertDoesNotThrow(() -> service.deleteUnit("1"));
        verify(repository, times(1)).existsById("1");
        verify(repository, times(1)).deleteById("1");
    }

    @Test
    void testGetAllUnits() {
        List<CombatUnit> units = new ArrayList<>();
        units.add(new CombatUnit());
        units.add(new CombatUnit());

        when(repository.findAll()).thenReturn(units);

        List<CombatUnit> result = service.getAllUnits();

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(repository, times(1)).findAll();
    }

    @Test
    void testGetUnitById() {
        CombatUnit unit = new CombatUnit();
        unit.setId("1");
        unit.setName("Test Unit");

        when(repository.findById("1")).thenReturn(Optional.of(unit));

        CombatUnit result = service.getUnitById("1");

        assertNotNull(result);
        assertEquals("1", result.getId());
        assertEquals("Test Unit", result.getName());
        verify(repository, times(1)).findById("1");
    }

    @Test
    void testGetUnitsBySide() {
        when(scenarioService.getActiveScenarioId()).thenReturn(null);
        List<CombatUnit> units = new ArrayList<>();
        CombatUnit unit1 = new CombatUnit();
        unit1.setSide("RED");
        units.add(unit1);
        CombatUnit unit2 = new CombatUnit();
        unit2.setSide("BLUE");
        units.add(unit2);

        when(repository.findAll()).thenReturn(units);

        List<CombatUnit> result = service.getUnitsBySide("RED");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("RED", result.get(0).getSide());
        verify(repository, times(1)).findAll();
    }

    @Test
    void testGetActiveUnits() {
        when(scenarioService.getActiveScenarioId()).thenReturn(null);
        List<CombatUnit> units = new ArrayList<>();
        CombatUnit unit1 = new CombatUnit();
        unit1.setStatus("ACTIVE");
        units.add(unit1);
        CombatUnit unit2 = new CombatUnit();
        unit2.setStatus("DESTROYED");
        units.add(unit2);

        when(repository.findAll()).thenReturn(units);

        List<CombatUnit> result = service.getActiveUnits();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ACTIVE", result.get(0).getStatus());
        verify(repository, times(1)).findAll();
    }

    @Test
    void testGetWeaponTemplates() {
        List<Weapon> result = service.getWeaponTemplates();

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(4, result.size());
    }
}
