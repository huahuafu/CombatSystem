package com.military.combat.simulation.commander;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IntentProsCons {
    private String intent;
    private List<String> pros = new ArrayList<>();
    private List<String> cons = new ArrayList<>();
}
