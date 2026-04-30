from __future__ import annotations

from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field


class PlanType(str, Enum):
    WIN_MAX = "WIN_MAX"
    LOSS_MIN = "LOSS_MIN"
    SPEED_MAX = "SPEED_MAX"
    BALANCED = "BALANCED"


class DeploymentItem(BaseModel):
    unitType: str
    unitCount: int = Field(ge=1)
    side: str
    latitude: float
    longitude: float
    facing: int = Field(default=60, ge=0, le=360)
    task: str


class RouteWaypoint(BaseModel):
    latitude: float
    longitude: float


class RouteItem(BaseModel):
    routeId: str
    forUnitType: str
    phase: str
    trigger: str
    waypoints: List[RouteWaypoint] = Field(min_length=2)


class SequenceStep(BaseModel):
    step: int = Field(ge=1)
    action: str
    actor: str
    target: str
    priority: str
    dependency: Optional[str] = None


class TempoNode(BaseModel):
    tPlusMin: int = Field(ge=0)
    milestone: str
    pace: str
    expectedOutcome: str


class IntentProsCons(BaseModel):
    intent: str
    pros: List[str] = Field(min_length=1)
    cons: List[str] = Field(min_length=1)


class StrategyCard(BaseModel):
    schemaVersion: str = "v1"
    label: PlanType
    scenarioId: str
    goal: str
    strategyId: str
    strategyName: str
    hypothesis: str
    riskSummary: str
    predictedWinRate: float = Field(ge=0, le=1)
    predictedExpectedLoss: float = Field(ge=0)
    predictedMissionSuccessRate: float = Field(ge=0, le=1)
    confidence: float = Field(ge=0, le=1)
    deploymentPlan: List[DeploymentItem] = Field(min_length=1)
    routePlan: List[RouteItem] = Field(min_length=1)
    sequencePlan: List[SequenceStep] = Field(min_length=1)
    tempoPlan: List[TempoNode] = Field(min_length=1)
    intentProsCons: IntentProsCons
    explanation: List[str] = Field(default_factory=list)


class GenerateRequest(BaseModel):
    scenarioId: str
    goal: str
    commanderSide: str = "RED"
    commanderRole: str = "ATTACK"
    scorePerspective: str = "RED"
    seed: Optional[int] = None


class GenerateResponse(BaseModel):
    requestId: str
    scenarioId: str
    goal: str
    strategies: List[StrategyCard]
    notes: List[str] = Field(default_factory=list)
