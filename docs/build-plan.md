# SlopShield Build Plan

This document outlines the incremental development phases for **Project SlopShield**.

## Phase 1: Foundation & The Message Bus
**Goal:** Establish the "Modular Monolith" architecture using Kotlin Coroutines and Channels.
- [ ] Define internal `Event` types: `StoryDiscovered`, `ContextResponse`, `AnalysisComplete`.
- [ ] Implement the `InternalBus` using Kotlin `Channels` for non-blocking communication.
- [ ] Scaffold the domain packages: `scout`, `memory`, `strategist`, `observability`.

## Phase 2: The Scout (Ingestion)
**Goal:** Fetch content from the outside world.
- [ ] Integrate with [HN Firebase API](https://github.com/HackerNews/API).
- [ ] Implement a periodic poller for top stories.
- [ ] Publish `StoryDiscovered` events to the Bus.

## Phase 3: The Memory (Contextual Knowledge)
**Goal:** Ingest the user's personal "Source of Truth."
- [ ] Implement a local file scanner for `.md` files.
- [ ] Add RSS feed parsing (e.g., Medium drafts).
- [ ] Implement a listener that responds to requests with a "Context Bundle" (`ContextResponse`).

## Phase 4: The Strategist (The Curator)
**Goal:** The AI brain of the operation.
- [ ] Create a robust wrapper for the `gemini` CLI.
- [ ] Implement the **SECV Scoring Rubric** (Mental Model Shift, Strategic Actionability, Signal Density, Durability).
- [ ] Implement the Dual-Path Analysis:
    - **Personal Alignment:** Echo Chamber vs. Opposite View.
    - **Hype Detection:** Genuine Insight vs. Hype Surfers.
- [ ] Generate the "Sparring Output" notes.

## Phase 5: Persistence & Observability
**Goal:** Stop "losing" state and start monitoring.
- [ ] Set up local **SQLite** via Exposed or SQLDelight.
- [ ] Track pipeline state: `DISCOVERED` -> `SCRAPING` -> `ANALYZING` -> `FILTERED`.
- [ ] Implement basic logging/metrics for the "Kafka-style" satire.

## Phase 6: Delivery & UI
**Goal:** A functional prototype for daily use.
- [ ] Build a minimalist Web UI (Ktor + HTMX or React).
- [ ] Display the **Signal** (high-score stories) and the **Noise Bin** (filtered slop).
- [ ] Add "Deep Dive" view to read the Sparring Notes.
