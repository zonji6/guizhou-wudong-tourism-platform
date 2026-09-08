from functools import lru_cache

from langgraph.checkpoint.memory import InMemorySaver
from langgraph.graph import END, START, StateGraph

from app.graph.nodes import (
    clarify,
    decide_intent,
    fast_lane,
    identify_intent,
    itinerary,
    knowledge_answer,
    pending_booking,
    retrieve,
    route_request,
)
from app.graph.state import AgentState


@lru_cache(maxsize=1)
def get_graph():
    return build_graph()


def build_graph():
    graph = StateGraph(AgentState)
    graph.add_node("fast_lane", fast_lane)
    graph.add_node("intent", identify_intent)
    graph.add_node("clarify", clarify)
    graph.add_node("retrieve", retrieve)
    graph.add_node("itinerary", itinerary)
    graph.add_node("knowledge", knowledge_answer)
    graph.add_node("pending_booking", pending_booking)
    graph.add_conditional_edges(START, route_request, {"fast": "fast_lane", "slow": "intent"})
    graph.add_edge("fast_lane", END)
    graph.add_conditional_edges("intent", decide_intent, {"clarify": "clarify", "itinerary": "retrieve", "knowledge": "retrieve", "booking": "pending_booking"})
    graph.add_conditional_edges("retrieve", lambda state: state.get("intent"), {"itinerary": "itinerary", "knowledge": "knowledge"})
    graph.add_edge("clarify", END)
    graph.add_edge("itinerary", END)
    graph.add_edge("knowledge", END)
    graph.add_edge("pending_booking", END)
    return graph.compile(checkpointer=InMemorySaver())
