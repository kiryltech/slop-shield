# SlopShield 🛡️

**"Filtering the Firehose through the Lens of Intent."**

SlopShield is a personalized AI "pre-reader" designed to combat **"AI Slop"**—low-signal, high-hype content that dominates modern technical feeds. It uses your own body of work (blog posts, technical drafts, engineering philosophies) as a "Source of Truth" to evaluate the relevance and value of daily content, specifically starting with Hacker News.

## 🚀 The Mission

In an era of AI-generated filler, SlopShield helps you:
*   **Maximize Cognitive ROI:** Spend 5 minutes on high-signal content instead of 60 minutes scrolling.
*   **Maintain Intellectual Integrity:** Explicitly surface "Opposite Views" and "Complementary" ideas while labeling "Echo Chambers."
*   **Detect Hype:** Separate "Hype Surfers" from "Genuine Insight" using a rigorous, personalized scoring rubric.

## 🧠 The Curator (AI Brain)

At the heart of SlopShield is **The Curator**, an AI agent that scores content using the **SECV-S Rubric**:
*   **(MMS) Mental Model Shift:** Does this change how I think?
*   **(SA) Strategic Actionability:** Can I make a decision based on this today?
*   **(SD) Signal Density:** Is it fluff-free?
*   **(D) Durability:** Will this matter in 2 years?
*   **(S) Serendipity:** Is it unexpectedly fascinating?

## 🏗️ Architecture

SlopShield is built as a **Modular Monolith** in Kotlin/JVM, featuring:
*   **Internal Message Bus:** Using Kotlin Coroutines and Channels for reactive domain boundaries.
*   **MapDB Persistence:** A zero-boilerplate, crash-consistent embedded NoSQL store.
*   **Gemini CLI Integration:** Leverages local AI for deep analysis while maintaining privacy and efficiency.
*   **High-Resolution Observability:** Detailed monitoring for every stage of the ingestion and analysis pipeline.

## 🛠️ Getting Started

### Prerequisites
*   JDK 21
*   [Gemini CLI](https://github.com/google/gemini-cli) installed and configured.

### Build & Run
```bash
./gradlew build
./gradlew run
```

## 📚 Documentation

For more detailed information, see:
*   [Design Document](docs/slopshield.md) - Deep dive into philosophy and architecture.
*   [Build Plan](docs/build-plan.md) - Incremental development phases.
