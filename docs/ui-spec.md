# SlopShield UI Specification: "The Curator's Lens"

**Goal**: Transform the SlopShield dashboard into a high-density, interactive "command center" designed for rapid cognitive ROI.

## 1. Core Philosophy
The UI should reflect the persona of a **Cynical Principal Engineer**: 
- **Signal First**: High-value information must be impossible to miss.
- **Density Over Fluff**: Maximize information per pixel; avoid excessive whitespace.
- **No Mystery Meat**: Every score and badge must be immediately understandable.

## 2. Layout: The Three-Pane Command Center

### A. Sidebar (Navigation & Controls)
- **Feeds**:
    - `Inbox`: All recent stories.
    - `High Signal`: Stories with SECV total score > 7.5.
    - `Opposite Views`: High-signal stories that challenge the personal context (Priority 1).
    - `Noise Bin`: Stories categorized as PRODUCT or SOURCE, or those with very low scores.
- **Context Monitor**:
    - Shows the status of the "Personal Lens" (Memory Service).
    - List of active context files (e.g., `CONTEXT.md`, `blog.xml`).
- **Activity Log**: Minimalist pulse indicating discovery, harvesting, and analysis progress.

### B. Center Feed (The Signal Stream)
- **Card-based List**:
    - **Prominent Score**: Large Signal Score (0-10) on the left of each card.
    - **Visual Category**: Color-coded left border based on story category.
    - **Badges**:
        - `Alignment`: (Echo Chamber, Opposite View, Complementary)
        - `Hype Risk`: (Low, Medium, High)
    - **Header**: Story Title (linking to source) and Domain.
    - **Snippet**: The first 100 characters of the **Sparring Note**.

### C. Right Pane (The Deep Dive)
- **SECV Breakdown**: Visual representation (Radar chart or Gauge) of MMS, SA, SD, and D.
- **The Sparring Note**: Full, unedited cynical engineering perspective.
- **Categorization Reasoning**: Why the AI put it in WRITING vs. PRODUCT.
- **Content Preview**: Scrollable view of the Markdown extracted by the Harvester.

## 3. Interaction Model
- **Real-time Updates**: The feed updates automatically as the pipeline finishes analysis (WebSocket).
- **Multi-dimensional Sorting**: Sort by Signal, Date, or MMS (Mental Model Shift).
- **Action Toolbar**:
    - `Read`: Mark as read (moves to archive).
    - `Slop`: Move to Noise Bin manually (trains local lens?).
    - `Deep Dive`: Opens the Right Pane for the selected story.

## 4. Visual Language & Branding
- **Theme**: Dark Mode by default. High contrast.
- **Colors**:
    - `Signal Gold`: `#f1c40f` (Score)
    - `Opposite View Emerald`: `#2ecc71` (High Priority)
    - `Echo Chamber Amber`: `#e67e22` (Lower Priority)
    - `Hype Red`: `#e74c3c` (High Risk)
- **Typography**:
    - Technical data: `JetBrains Mono`
    - Content: `Inter` or `System Sans-Serif`

## 5. Proposed Tech Stack
- **Frontend**: HTMX + Tailwind CSS (via Ktor HTML DSL or static assets).
- **Interactivity**: Server-Sent Events (SSE) or WebSockets for the live stream.
- **Charts**: Mermaid.js or simple CSS-based gauges for the SECV breakdown.
