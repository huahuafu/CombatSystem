package com.military.combat.util;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.GroupCommandOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavalDomainValidatorTest {

    @Test
    void shouldNormalizeUnitFieldsToUpperCase() {
        CombatUnit unit = new CombatUnit();
        unit.setDomain("sea");
        unit.setVesselType("destroyer");
        unit.setNavalTaskType("escort");
        unit.setSupplyStatus("low");
        unit.setDetectionChainStatus("open");
        unit.setFormationRole("screen");

        NavalDomainValidator.normalizeAndValidateUnit(unit);

        assertEquals("SEA", unit.getDomain());
        assertEquals("DESTROYER", unit.getVesselType());
        assertEquals("ESCORT", unit.getNavalTaskType());
        assertEquals("LOW", unit.getSupplyStatus());
        assertEquals("OPEN", unit.getDetectionChainStatus());
        assertEquals("SCREEN", unit.getFormationRole());
    }

    @Test
    void shouldRejectInvalidOrderType() {
        GroupCommandOrder order = new GroupCommandOrder();
        order.setOrderType("fire_and_forget");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> NavalDomainValidator.normalizeAndValidateCommandOrder(order)
        );

        assertTrue(ex.getMessage().contains("orderType"));
    }

    @Test
    void shouldNormalizeElectromagneticAlias() {
        CombatUnit unit = new CombatUnit();
        unit.setDomain("em");

        NavalDomainValidator.normalizeAndValidateUnit(unit);

        assertEquals("ELECTROMAGNETIC", unit.getDomain());
    }
}
