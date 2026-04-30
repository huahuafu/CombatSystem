from __future__ import annotations

import uuid
from .models import (
    DeploymentItem,
    GenerateRequest,
    GenerateResponse,
    IntentProsCons,
    PlanType,
    RouteItem,
    RouteWaypoint,
    SequenceStep,
    StrategyCard,
    TempoNode,
)


def _build_strategy(req: GenerateRequest, plan_type: PlanType, idx: int) -> StrategyCard:
    base_lat = 22.8 + idx * 0.04
    base_lng = 121.4 + idx * 0.03
    pace = "FAST" if plan_type == PlanType.SPEED_MAX else "MEDIUM"
    loss = 0.22 if plan_type == PlanType.LOSS_MIN else (0.36 if plan_type == PlanType.SPEED_MAX else 0.28)
    win = 0.76 if plan_type == PlanType.WIN_MAX else (0.71 if plan_type == PlanType.BALANCED else 0.67)
    success = 0.81 if plan_type == PlanType.SPEED_MAX else (0.78 if plan_type == PlanType.WIN_MAX else 0.72)

    return StrategyCard(
        label=plan_type,
        scenarioId=req.scenarioId,
        goal=req.goal,
        strategyId=f"{plan_type.value}-{idx + 1}",
        strategyName=f"{plan_type.value} 战术包",
        hypothesis="通过六阶段协同缩短闭环时间并提升目标达成率。",
        riskSummary="主要风险是补给窗口暴露与侧翼穿插带来的协同压力。",
        predictedWinRate=win,
        predictedExpectedLoss=loss,
        predictedMissionSuccessRate=success,
        confidence=0.74,
        deploymentPlan=[
            DeploymentItem(
                unitType="DESTROYER",
                unitCount=2,
                side=req.commanderSide,
                latitude=base_lat,
                longitude=base_lng,
                facing=60,
                task="主攻压制",
            )
        ],
        routePlan=[
            RouteItem(
                routeId=f"R-{plan_type.value}",
                forUnitType="DESTROYER",
                phase="PHASE_1",
                trigger="侦察确认目标暴露",
                waypoints=[
                    RouteWaypoint(latitude=base_lat, longitude=base_lng),
                    RouteWaypoint(latitude=base_lat + 0.12, longitude=base_lng + 0.12),
                ],
            )
        ],
        sequencePlan=[
            SequenceStep(step=1, action="情报侦察", actor="UAV_RECON", target="前沿据点", priority="HIGH"),
            SequenceStep(step=2, action="火力打击", actor="DESTROYER", target="高价值目标", priority="HIGH", dependency="step-1"),
        ],
        tempoPlan=[
            TempoNode(tPlusMin=0, milestone="展开", pace=pace, expectedOutcome="编队进入攻击起始线"),
            TempoNode(tPlusMin=12, milestone="压制", pace=pace, expectedOutcome="完成前沿压制与局部控场"),
        ],
        intentProsCons=IntentProsCons(
            intent=f"{plan_type.value} 下优先服务 {req.commanderRole} 的目标达成。",
            pros=["结构化执行清晰", "可直接用于推演执行"],
            cons=["依赖侦察质量", "跨域协同成本较高"],
        ),
        explanation=["四类策略固定输出结构，便于前后端统一渲染。"],
    )


def generate_strategies(req: GenerateRequest) -> GenerateResponse:
    cards = [
        _build_strategy(req, PlanType.WIN_MAX, 0),
        _build_strategy(req, PlanType.LOSS_MIN, 1),
        _build_strategy(req, PlanType.SPEED_MAX, 2),
        _build_strategy(req, PlanType.BALANCED, 3),
    ]
    return GenerateResponse(
        requestId=str(uuid.uuid4()),
        scenarioId=req.scenarioId,
        goal=req.goal,
        strategies=cards,
        notes=["FastAPI输出为strategy-schema v1", "四类方案字段均为强校验"],
    )
