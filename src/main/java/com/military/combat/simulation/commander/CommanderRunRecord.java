package com.military.combat.simulation.commander;

import com.military.combat.simulation.KillChainRunResult;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "commander_runs")
public class CommanderRunRecord {
    @Id
    private String requestId;
    @Indexed
    private String scenarioId;
    private String goal;
    private CommanderStrategyLabel label;
    private String strategyId;
    @Indexed
    private long createdAt;
    /** 推演 seed（用于复现）。 */
    private long seed;
    /** 引擎版本（用于对比不同版本行为差异）。 */
    private String engineVersion;
    /** 执行时请求的回合数（用于复现/审计）。 */
    private int roundsRequested;

    private CommanderBattleReport report;
    private List<KillChainRunResult> rounds = new ArrayList<>();
}

