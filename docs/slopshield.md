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
*   **Persistence:** Local **SQLite** for long-term storage of signal and noise.

---

## **3. DOMAIN ARCHITECTURE & COMPONENTS**

### **A. The Scout (Ingestion Domain)**
*   **Responsibility:** Periodically polls the [HN Firebase API](https://github.com/HackerNews/API).
*   **Behavior:** Publishes a `StoryDiscovered` event to the internal Bus.

### **B. The Memory (Contextual Domain)**
*   **Responsibility:** Aggregates local `.md` drafts and Medium RSS content.
*   **Behavior:** Provides the "Context Bundle" for **The Curator**.

### **C. The Strategist (Analysis Domain - "The Curator")**
*   **Responsibility:** Orchestrates the AI pipeline.
*   **The SECV Scoring Rubric (1-10):**
    *   **MMS (Mental Model Shift):** Does this change how I think, or just confirm what I know?
    *   **SA (Strategic Actionability):** Can I make a decision based on this today?
    *   **SD (Signal Density):** Is it "fluff-free"?
    *   **D (Durability):** Will this matter in 2 years?
*   **Dual-Path Analysis:**
    1.  **Personal Alignment:** `Echo Chamber`, `Opposite View`, or `Complementary`.
    2.  **Hype Detection:** Identifies "Hype Surfers" vs. "Genuine Insight."
*   **Sparring Output:** Generates a **"Why this matters to you"** note (e.g., challenges your "Kafka Cult" thesis).

### **D. Observability (The Progress Domain)**
*   **Responsibility:** Maintains a live state of the pipeline (`DISCOVERED` -> `SCRAPING` -> `ANALYZING`).
*   **Satire:** High-resolution monitoring for a low-volume stream.

---

## **4. THE BUS PROTOCOL (INTERNAL EVENTS)**

| Event | Producer | Description |
| :--- | :--- | :--- |
| `StoryDiscovered` | Scout | Contains story metadata and URL. |
| `ContextResponse` | Memory | Returns the aggregated text of your body of work. |
| `AnalysisComplete` | Strategist | Contains SECV scores, Alignment labels, and **Hype/Noise Red Flags**. |

---

## **5. V1 SCOPE (DEFINITION OF DONE)**
*   [ ] Kotlin/JVM project structure with clear package boundaries.
*   [ ] **Scout** service polling top 30 HN stories.
*   [ ] **Memory** service reading local markdown files and RSS.
*   [ ] **Strategist (The Curator)** service wrapping `gemini` CLI calls.
*   [ ] **Internal Bus** implemented using Kotlin `Channels`.
*   [ ] Minimalist **Web UI** showing the **Signal** and the **Noise Bin**.
*   [ ] **Zero instances of Kafka or Kubernetes.**
