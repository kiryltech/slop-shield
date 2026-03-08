# SlopShield Build Plan

This document outlines the incremental development phases for **Project SlopShield**.

## Phase 1: Foundation & The Domain Event Stream
**Goal:** Establish the "Modular Monolith" architecture using Kotlin Coroutines and Channels.
- [x] Define internal `Event` types: `StoryDiscovered`, `HarvestComplete`, `ContextResponse`, `AnalysisComplete`.
- [x] Implement the `InternalDomainEventStream` using Kotlin `Channels` for non-blocking communication.
- [x] Scaffold the domain packages: `scout`, `harvester`, `memory`, `strategist`, `observability`.

## Phase 2: The Scout & The Harvester
**Goal:** Fetch content and extract clean text.
- [x] Integrate with [HN Firebase API](https://github.com/HackerNews/API).
- [x] Implement a periodic poller for top stories in the **Scout**.
- [x] Implement **The Harvester** using the Gemini CLI for initial URL-to-Text extraction.
- [x] Orchestrate the flow: `StoryDiscovered` -> `HarvestComplete`.

## Phase 3: The Memory (Contextual Knowledge)
**Goal:** Ingest the user's personal "Source of Truth."
- [ ] Implement a local file scanner for `.md` files.
- [ ] Add RSS feed parsing (e.g., Medium drafts).
- [ ] Implement a listener that responds to requests with a "Context Bundle" (`ContextResponse`).

## Phase 4: The Strategist (The Curator)
**Goal:** The AI brain of the operation.
- [ ] Create a robust wrapper for the `gemini` CLI for analysis.
- [ ] Implement the **SECV Scoring Rubric** (Mental Model Shift, Strategic Actionability, Signal Density, Durability).
- [ ] Implement the Dual-Path Analysis:
    - **Personal Alignment:** Echo Chamber vs. Opposite View (Priority on High-Signal Disagreement).
    - **Hype Detection:** Genuine Insight vs. Hype Surfers.
- [ ] Generate the "Sparring Output" notes.

## Phase 5: Persistence & Observability
**Goal:** Stop "losing" state and start monitoring.
- [ ] Set up **MapDB** with `transactionEnable()` and `BTreeMap` for sorted story indexing.
- [ ] Track pipeline state: `DISCOVERED` -> `HARVESTING` -> `ANALYZING` -> `FILTERED`.
- [ ] Implement basic logging/metrics for the "Satirical Observability" domain.

## Phase 6: Delivery & UI
**Goal:** A functional prototype for daily use.
- [ ] Build a minimalist Web UI (Ktor + HTMX or React).
- [ ] Display the **Signal** (high-score stories) and the **Noise Bin** (filtered slop).
- [ ] Add "Deep Dive" view to read the Sparring Notes.
