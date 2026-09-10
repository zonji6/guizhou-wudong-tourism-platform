from typing import Any

from langgraph.graph import END, START, StateGraph

from app.graph.context import RunContext
from app.graph.nodes import (
    itinerary_planner,
    knowledge_guide,
    route_selected_agent,
    select_agent,
    service_recommender,
)
from app.graph.state import WorkflowState


def get_graph(checkpointer: Any | None = None):
    graph = StateGraph(WorkflowState, context_schema=RunContext)
    graph.add_node("select_agent", select_agent)
    graph.add_node("knowledge_guide", knowledge_guide)
    graph.add_node("service_recommender", service_recommender)
    graph.add_node("itinerary_planner", itinerary_planner)
    graph.add_edge(START, "select_agent")
    graph.add_conditional_edges(
        "select_agent",
        route_selected_agent,
        {
            "KNOWLEDGE_GUIDE": "knowledge_guide",
            "SERVICE_RECOMMENDER": "service_recommender",
            "ITINERARY_PLANNER": "itinerary_planner",
        },
    )
    graph.add_edge("knowledge_guide", END)
    graph.add_edge("service_recommender", END)
    graph.add_edge("itinerary_planner", END)
    return graph.compile(checkpointer=checkpointer)
