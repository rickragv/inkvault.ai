<div align="center">

# InkVault.ai

### On-Device Investigative Intelligence

**Air-gapped document analysis powered by Gemma 4 LLM**
**Zero cloud. Zero leakage. Zero compromise.**

<br/>

<img src="docs/demo.gif" alt="InkVault.ai" width="320"/>

<br/><br/>

![Platform](https://img.shields.io/badge/Platform-Android-34A853?style=for-the-badge&logo=android&logoColor=white)
![AI](https://img.shields.io/badge/AI-Gemma%204-4285F4?style=for-the-badge&logo=google&logoColor=white)
![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-1A237E?style=for-the-badge&logo=lock&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)

</div>

---

<br/>

## The Problem

Investigative journalists working on sensitive stories face an impossible tradeoff: **use powerful AI tools and risk source exposure**, or stay offline and lose weeks to manual document analysis. Cloud-based AI means every query, every document, every entity name passes through third-party servers.

When the story involves government corruption, shell companies, or financial fraud, that tradeoff can endanger lives.

<br/>

## The Solution

InkVault.ai brings the full power of a large language model to the journalist's device with **zero data exfiltration**. Powered by Google's Gemma 4 running via LiteRT-LM, the entire intelligence pipeline operates within the application sandbox. No network permissions. No telemetry. No analytics.

The app doesn't just read documents. It **understands** them.

<br/>

---

<br/>

## Core Capabilities

<br/>

### Agentic Document Intelligence

InkVault operates as an **autonomous AI agent**, not a simple chatbot. When a journalist asks a question, Gemma 4 independently decides which tools to invoke, executes them against the local database, and synthesizes findings into a coherent answer with source citations.

The system supports **17 on-device investigative tools** spanning entity analysis, contradiction detection, financial forensics, red flag scanning, timeline reconstruction, and deep analytical reasoning. All tool definitions are externalized in configuration, making the system extensible without code changes.

<br/>

### Dual-Engine OCR Pipeline

Documents enter the system through a two-stage extraction pipeline:

| Stage | Engine | Purpose |
|:------|:-------|:--------|
| **Pass 1** | Google ML Kit | Pixel-level text recognition with bounding boxes |
| **Pass 2** | Gemma 4 Vision | Multimodal cross-reference to correct OCR errors in Hindi, Urdu, Arabic, and regional scripts |

The dual-engine approach achieves significantly higher accuracy on government documents with mixed scripts, faded thermal prints, and non-standard layouts.

<br/>

### Entity Graph Construction

Every ingested document is automatically parsed for **seven entity types**: persons, companies, shell entities, monetary amounts, dates, locations, and official designations. The system extracts not just entities but the **relationships between them** and stores these as a queryable graph.

The force-directed visualization renders entity networks on a Compose Canvas with interactive pan and zoom, revealing hidden connections between documents that would take weeks to map manually.

<br/>

### Contradiction Detection

The system automatically cross-references statements across documents to surface inconsistencies:

- **Date conflicts** between audit records and transaction logs
- **Amount mismatches** between declared values and actual transfers
- **Statement contradictions** where different documents assert conflicting facts
- **Entity discrepancies** where the same entity appears with different attributes

This is the Panama Papers use case: the story lives in the gaps between documents.

<br/>

### Hybrid RAG Search

Queries are answered through a weighted retrieval pipeline combining **full-text search** (FTS4/FTS5 keyword matching) with **TF-IDF cosine similarity** (semantic matching). Retrieved context is assembled with source metadata and fed to Gemma 4 for grounded, citation-backed responses.

<br/>

---

<br/>

## Architecture

```
                              InkVault.ai
                         Android / Jetpack Compose
 ================================================================

    UI                Chat  |  Docs  |  Graph  |  Timeline
                        |         |         |          |
    ViewModels       ChatVM   DocsVM   GraphVM   TimelineVM
                        |         |         |          |
 ----------------------------------------------------------------
    ML Layer         DocumentProcessingPipeline
                     +-----------------------------------------+
                     |  ImagePreprocessor -> MlKitOCR          |
                     |  GemmaInferenceEngine (LiteRT-LM)       |
                     |  NerExtractor (combined extraction)      |
                     |  ContradictionAnalyzer                   |
                     |  EmbeddingEngine (TF-IDF)                |
                     |  RagPipeline (FTS + vector)              |
                     |  InvestigationToolSet (17 tools)         |
                     +-----------------------------------------+
 ----------------------------------------------------------------
    Data Layer       Room Database
                     +------------------------------------------+
                     |  documents        extracted_entities      |
                     |  entity_relationships   contradictions    |
                     |  timeline_events   document_embeddings    |
                     |  chat_sessions     chat_messages          |
                     |  documents_fts (full-text search index)   |
                     +------------------------------------------+
 ----------------------------------------------------------------
    DI               Hilt (AppModule + DatabaseModule)
    Config           edgeai_config.json -> AppConfig
 ----------------------------------------------------------------
    On-Device        LiteRT-LM 0.10.0    |    ML Kit OCR 16.0.1
    Inference        Gemma 4 E2B (2.4GB) |    CPU-only (~10MB)
                     GPU / CPU fallback   |    Latin + Devanagari
 ================================================================
```

<br/>

---

<br/>

## Technology

| | Technology | Details |
|:---|:---|:---|
| **Language** | Kotlin 2.2.10 | Coroutines, Flow, KSP |
| **UI** | Jetpack Compose | Material 3, Compose Canvas, Navigation |
| **LLM** | Gemma 4 E2B | 2.4GB quantized, on-device via LiteRT-LM 0.10.0 |
| **OCR** | ML Kit Text Recognition | On-device, CPU-only, 16.0.1 |
| **Database** | Room 2.7.1 | FTS4/FTS5, 8 entities, 7 DAOs |
| **DI** | Hilt 2.56.2 | Singleton scoped ML components |
| **Search** | Hybrid RAG | FTS keyword + TF-IDF vector, weighted merge |
| **Camera** | CameraX 1.4.1 | Document capture with preview |
| **Images** | Coil 3.0.4 | Async image loading |
| **Config** | JSON externalized | All prompts, tools, thresholds configurable |
| **Target** | Android 8.0 - 15 | API 26 - 35 |

<br/>

### Multilingual Support

<div align="center">

`English` `Hindi` `Urdu` `Tamil` `Telugu` `Bengali` `Marathi` `Gujarati` `Kannada` `Malayalam` `Punjabi` `Odia` `Assamese` `Nepali` `Sinhala` `Arabic` `Farsi` `Pashto` `Sindhi` `Kashmiri`

</div>

<br/>

---

<br/>

## Getting Started

### Prerequisites

- Android Studio (latest stable)
- Gemma 4 E2B model file (2.4GB)

### Build

```bash
git clone <repo-url>
cd edge_ai
./gradlew assembleDebug
```

### Deploy Model

```bash
adb push gemma-4-E2B-it.litertlm /sdcard/Download/
```

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

<br/>

---

<br/>

## Configuration

All intelligence parameters are externalized in `edgeai_config.json`. No hardcoded strings in application code.

| Section | Controls |
|:--------|:---------|
| `model` | Inference parameters: temperature, top_k, top_p, max_tokens |
| `tools` | 17 tool definitions with schemas and descriptions |
| `prompts` | 9 prompt templates for extraction, analysis, and chat |
| `rag` | Chunk size, overlap, FTS/vector weights, retrieval depth |
| `processing` | PDF limits, OCR thresholds, contradiction detection |
| `preprocessing` | Image enhancement: CLAHE, binarization, contrast |
| `supported_languages` | 20 language codes for multilingual extraction |

<br/>

---

<br/>

<div align="center">

**75 source files** &middot; **17 agentic tools** &middot; **8 Room entities** &middot; **20 languages** &middot; **9 prompt templates**

<br/>

*Built for journalists who can't afford to trust the cloud.*

<br/>

</div>

---

## License

Proprietary. All rights reserved.
