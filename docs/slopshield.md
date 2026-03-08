# **DESIGN DOCUMENT: PROJECT SLOPSHIELD**
**"Filtering the Firehose through the Lens of Intent."**

## **1. THE MISSION (WHY)**
**SlopShield** is a personalized AI "pre-reader" designed to combat **"AI Slop"**—low-signal, high-hype content that dominates modern technical feeds. It treats your own body of work—blog posts, technical drafts, and engineering philosophies—as a "Source of Truth" to evaluate the relevance of the daily Hacker News cycle.

The internal intelligence driving the analysis is known as **The Curator**.

### **Key Objectives:**
*   **Cognitive ROI:** Spend 5 minutes on high-signal content instead of 60 minutes scrolling through "slop."
*   **Intellectual Integrity:** Explicitly label "Echo Chambers" vs. "Opposite Views" via **The Curator's** specialized lens.
*   **Architectural Satire:** A **Modular Monolith** in Kotlin/JVM that mocks the "Kafka Cult" by using a message-driven core that *could* be distributed but chooses to be efficient.

---

## **2. TECHNICAL STACK & CONSTRAINTS**

*   **Platform:** **Kotlin (JVM)**. Chosen for robust multi-threading and type-safe service boundaries.
*   **Concurrency:** Kotlin **Coroutines** & **Channels** (simulating the internal Message Bus).
*   **AI Engine:** **The Curator** (a Gemini CLI Wrapper). The system executes local Gemini CLI sessions to perform deep analysis while maintaining local auth and cost efficiency.
*   **Memory Strategy:** **Full-Context Injection**. The "Memory" module aggregates the entire local corpus into the LLM context window for V1.
*   **Persistence:** **MapDB** (Embedded NoSQL).
    *   *Why MapDB?* Provides a "zero-boilerplate" experience by persisting standard Kotlin Maps to disk.
    *   *Implementation:* Uses `BTreeMap` (implementing `NavigableMap`) for sorted indexing of stories and events.
    *   *Reliability:* Configured with Write-Ahead Logging (WAL) and ACID transactions to ensure data consistency even during abnormal shutdowns.
    *   *Alternatives Considered:*
        *   **SQLite:** Traditional, but requires SQL mapping/migrations.
        *   **Xodus (JetBrains):** Modern and "Kotlin-idiomatic," but slightly more complex than simple Maps.
        *   **H2:** SQL-based and JVM-native, but carries more overhead than needed for simple signal storage.

---

## **3. DOMAIN ARCHITECTURE & COMPONENTS**

### **A. The Scout (Ingestion Domain)**
*   **Responsibility:** Periodically polls the [HN Firebase API](https://github.com/HackerNews/API).
*   **Behavior:** Publishes a `StoryDiscovered` event to the internal Bus.

### **B. The Harvester (Scraping Domain)**
*   **Responsibility:** Extracts clean text from external URLs.
*   **Behavior:** Initially delegates scraping to **The Curator** (Gemini CLI) for rapid prototyping, with future plans for a deterministic, headless-browser-based "Harvester" service.

### **C. The Memory (Contextual Domain)**
*   **Responsibility:** Aggregates local `.md` drafts and Medium RSS content.
*   **Behavior:** Provides the "Context Bundle" for **The Curator**.

### **D. The Strategist (Analysis Domain - "The Curator")**
*   **Responsibility:** Orchestrates the AI pipeline.
*   **The SECV Scoring Rubric (Strategic Engineering & Cognitive Value):**
    A specialized framework designed to bridge the gap between "interesting news" and "actionable engineering strategy."
    *   **MMS (Mental Model Shift):** Does this change how you think, or just confirm what you already know?
    *   **SA (Strategic Actionability):** Can you make a concrete decision or take action based on this today?
    *   **SD (Signal Density):** Is it "meat" (high-information) or "fluff" (low-signal filler)?
    *   **D (Durability):** Will this information still be relevant and valuable in 2 years?
*   **Dual-Path Analysis:**
    1.  **Personal Alignment:** `Echo Chamber`, `Opposite View`, or `Complementary`.
    2.  **Hype Detection:** Identifies "Hype Surfers" vs. "Genuine Insight."
*   **Goal:** Surface **High-Signal Disagreement**. Opposite views with high scores are prioritized over Aligned views (which are labeled as "Echo Chambers").

### **E. Observability (The Progress Domain)**
*   **Responsibility:** Maintains a live state of the pipeline (`DISCOVERED` -> `HARVESTING` -> `ANALYZING`).
*   **Satire:** High-resolution monitoring for a low-volume stream.

---

## **4. THE BUS PROTOCOL (INTERNAL EVENTS)**

| Event | Producer | Description |
| :--- | :--- | :--- |
| `StoryDiscovered` | Scout | Contains story metadata and URL. |
| `HarvestComplete` | Harvester | Contains extracted clean text. |
| `ContextResponse` | Memory | Returns the aggregated text of your body of work. |
| `AnalysisComplete` | Strategist | Contains SECV scores, Alignment labels, and **Hype/Noise Red Flags**. |

---

## **5. EARLY CONCEPT: SAMPLE OUTPUT**

**HN Story:** *"Why the Message Bus is a Cognitive Burden"*
**URL:** `https://example.com/anti-kafka-rant`

**The Curator's Assessment:**
*   **SECV Score:** 8.25/10
*   **MMS:** 9 | **SA:** 4 | **SD:** 8 | **D:** 7
*   **Alignment:** **OPPOSITE VIEW** (High Contrast to your "Modular Monolith" thesis).
*   **Hype Risk:** Low (Original argument, no buzzwords).

**Sparring Note:**
*"This article directly challenges your SlopShield architecture. It argues that internal message buses lead to 'Event Spaghetti' and hidden state mutations. While it reinforces your skepticism of Kafka, it suggests your 'Satirical Bus' might be the very thing you're mocking. **Must read to avoid building a trap.**"*

---

## **6. V1 SCOPE (DEFINITION OF DONE)**
*   [ ] Kotlin/JVM project structure with clear package boundaries.
*   [ ] **Scout** service polling top 30 HN stories.
*   [ ] **Harvester** integration via Gemini CLI.
*   [ ] **Memory** service reading local markdown files.
*   [ ] **Strategist (The Curator)** service wrapping `gemini` CLI calls.
*   [ ] **Internal Bus** implemented using Kotlin `Channels`.
*   [ ] Minimalist **Web UI** showing the **Signal** and the **Noise Bin**.
*   [ ] **Zero instances of Kafka or Kubernetes.**
