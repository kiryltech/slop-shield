# SlopShield Build Plan

This document outlines the incremental development phases for **Project SlopShield**.

## Phase 1: Foundation & The Domain Event Stream
**Goal:** Establish the reactive "Modular Monolith" architecture using Kotlin Coroutines and Flows.
- [x] Define internal `Event` types: `StoryDiscovered`, `HarvestComplete`, `ContextResponse`, `AnalysisComplete`.
- [x] Implement the `EventCoordinator` using Kotlin `SharedFlow` and reflection-based discovery.
- [x] Scaffold the domain packages: `scout`, `harvester`, `memory`, `strategist`, `observability`.

## Phase 2: The Scout & The Harvester
**Goal:** Fetch content and extract clean text.
- [x] Integrate with [HN Firebase API](https://github.com/HackerNews/API).
- [x] Implement a periodic poller for top stories in the **Scout**.
- [x] Implement **The Harvester** using the Gemini CLI for initial URL-to-Text extraction.
- [x] Orchestrate the flow: `StoryDiscovered` -> `HarvestComplete`.

## Phase 3: The Memory (Contextual Knowledge)
**Goal:** Ingest the user's personal "Source of Truth."
- [x] Implement a local file scanner for `.md`, `.txt`, and `.xml` files.
- [x] Implement a listener that responds to requests with a "Context Bundle" (`ContextResponse`).
- [x] Support personalized instructions via `CONTEXT.md`.

## Phase 4: The Strategist (The Curator)
**Goal:** The AI brain of the operation.
- [x] Create a robust wrapper for the `gemini` CLI for analysis (Implemented as `AIService`).
- [x] Implement initial story categorization (Implemented as `Categorizer`).
- [ ] Implement the **SECV Scoring Rubric** (Mental Model Shift, Strategic Actionability, Signal Density, Durability).
- [ ] Implement the Dual-Path Analysis:
    - **Personal Alignment:** Echo Chamber vs. Opposite View (Priority on High-Signal Disagreement).
    - **Hype Detection:** Genuine Insight vs. Hype Surfers.
- [ ] Generate the "Sparring Output" notes.

## Phase 5: Persistence & Observability
**Goal:** Stop "losing" state and start monitoring.
- [x] Set up **MapDB** with `transactionEnable()` and `BTreeMap` for sorted story indexing.
- [x] Implement **StoryProjector** for centralized state management.
- [x] Track pipeline state through **ProjectableEvent** pattern.
- [x] Implement **HarvestDumper** for debug disk persistence.
- [ ] Implement basic logging/metrics for the "Satirical Observability" domain.

## Phase 6: Delivery & UI
**Goal:** A functional prototype for daily use.
- [x] Build a minimalist Web UI (Ktor + HTML DSL).
- [ ] Display the **Signal** (high-score stories) and the **Noise Bin** (filtered slop).
- [ ] Add "Deep Dive" view to read the Sparring Notes.

## Phase 7: Future Iterations & Polishing
- [ ] Transition to a rich, interactive UI (React or Compose HTML).
- [ ] Implement real-time dashboard updates via WebSockets.
- [ ] Add "Deep Dive" visualizations for SECV scoring distributions.
