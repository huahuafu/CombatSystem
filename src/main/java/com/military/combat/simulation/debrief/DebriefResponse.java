package com.military.combat.simulation.debrief;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebriefResponse {
    private List<String> bullets = new ArrayList<>();
    /** 一段话复盘；未启用 LLM 时与 bullet 文本等价拼接 */
    private String narrative;
    /** RULES：仅规则；HYBRID：规则 + LLM 叙事 */
    private String source;
}
