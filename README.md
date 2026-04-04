<div align="center">

<img src="https://img.shields.io/badge/-%F0%9F%94%92%20InkVault.ai-1A237E?style=for-the-badge&labelColor=0D1259&logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJ3aGl0ZSI+PHBhdGggZD0iTTEyIDJMNCAxOHY0YzAgNS41IDMuNSAxMC41IDggMTEuNyA0LjUtMS4yIDgtNi4yIDgtMTEuN1Y0eiIvPjwvc3ZnPg==&logoColor=white" alt="InkVault.ai" height="45"/>

<br/><br/>

### ⚡ On-Device Investigative Intelligence

**Air-gapped document analysis powered by Gemma 4 LLM**

<br/>

<img src="docs/demo.gif" alt="InkVault.ai" width="320"/>

<br/><br/>

[![Platform](https://img.shields.io/badge/Android-34A853?style=flat-square&logo=android&logoColor=white)](#)
[![AI](https://img.shields.io/badge/Gemma%204-4285F4?style=flat-square&logo=google&logoColor=white)](#)
[![Privacy](https://img.shields.io/badge/100%25%20Offline-E91E63?style=flat-square&logo=shieldsdotio&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin%202.2-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](#)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](#)
[![Room](https://img.shields.io/badge/Room%20DB-FF6F00?style=flat-square&logo=sqlite&logoColor=white)](#)
[![Hilt](https://img.shields.io/badge/Hilt%20DI-34A853?style=flat-square&logo=dagger&logoColor=white)](#)
[![LiteRT](https://img.shields.io/badge/LiteRT--LM-EA4335?style=flat-square&logo=tensorflow&logoColor=white)](#)

<br/>

> *Built for journalists who can't afford to trust the cloud.*

</div>

<br/>

---

<br/>

## 🔐 Why InkVault?

<table>
<tr>
<td width="80" align="center">

🚫☁️

</td>
<td>

**The Problem** — Investigative journalists face an impossible choice: use powerful AI tools and risk source exposure, or stay offline and lose weeks to manual document analysis.

</td>
</tr>
<tr>
<td align="center">

🛡️📱

</td>
<td>

**The Solution** — InkVault runs the full power of Gemma 4 LLM entirely on your phone. No internet permission. No API keys. No telemetry. Your documents never leave the device sandbox.

</td>
</tr>
</table>

<br/>

---

<br/>

## ✨ Features

<br/>

<details open>
<summary><h3>🔒 Air-Gap Security</h3></summary>

<br/>

| | |
|:--|:--|
| 🚫 | No internet permission in the Android manifest |
| 🧠 | All AI inference runs on-device via LiteRT-LM |
| 📡 | Zero telemetry, analytics, cloud sync, or API calls |
| 🔐 | Documents and entities never leave the app sandbox |
| 🌍 | Designed for hostile environments and source protection |

</details>

<br/>

<details open>
<summary><h3>🤖 Agentic AI — 17 On-Device Tools</h3></summary>

<br/>

The LLM doesn't just chat — it **autonomously decides** which tools to invoke, executes them, and synthesizes findings into clean answers with source citations.

<br/>

<table>
<tr>
<th align="left">🔍 Search & Retrieval</th>
<th align="left">👤 Entity Intelligence</th>
</tr>
<tr>
<td>

- `search_documents` — Full-text + semantic
- `search_document_by_title` — By name or number
- `get_document_stats` — Corpus overview

</td>
<td>

- `extract_entities` — 7 entity types via NER
- `query_entities` — Track across all docs
- `list_entities_by_type` — All people, companies...
- `find_cross_document_entities` — Multi-doc links
- `find_most_frequent_entities` — Ranked

</td>
</tr>
<tr>
<th align="left">⚖️ Forensic Analysis</th>
<th align="left">📊 Synthesis</th>
</tr>
<tr>
<td>

- `find_contradictions` — Two-doc comparison
- `find_all_contradictions` — Corpus-wide scan
- `compare_events` — Chronological ordering
- `query_financial_data` — Money flows
- `scan_for_red_flags` — Suspicious patterns

</td>
<td>

- `summarize_document` — Key findings
- `summarize_corpus` — Full investigation
- `deep_analysis` — Complex reasoning
- `build_timeline` — Event reconstruction

</td>
</tr>
</table>

</details>

<br/>

<details open>
<summary><h3>📷 Dual-Engine OCR Pipeline</h3></summary>

<br/>

```
📸 Camera / Gallery
    │
    ▼
┌─────────────────────────────────────┐
│  Pass 1: ML Kit OCR (~2.5s)        │  ◀── Pixel-level text recognition
│  Pass 2: Gemma 4 Vision            │  ◀── Multimodal cross-reference
│  ✓ Hindi ✓ Urdu ✓ Arabic ✓ 17+    │      for error correction
└─────────────────────────────────────┘
    │
    ▼
  Corrected multilingual text
```

- 🔤 Adaptive preprocessing for faded prints and non-standard layouts
- 🎨 CLAHE contrast enhancement with automatic quality detection
- 🌐 20 languages supported natively

</details>

<br/>

<details open>
<summary><h3>⚙️ Automated Intelligence Pipeline</h3></summary>

<br/>

Every document triggers automatic analysis on ingestion:

```
 📄 Document
  │
  ├──▶ 🔤  OCR extraction
  ├──▶ 🤖  Gemma 4 cross-reference
  ├──▶ 👤  Named entity recognition (7 types)
  ├──▶ 🔗  Relationship extraction (graph edges)
  ├──▶ 📅  Timeline event extraction (ISO-8601)
  ├──▶ 📊  TF-IDF embedding (semantic search)
  └──▶ ⚠️  Contradiction detection vs corpus
```

- ⚡ Combined single-prompt extraction — **3x faster** than sequential calls
- 🔄 Runs on background thread — UI stays responsive

</details>

<br/>

<details open>
<summary><h3>🕸️ Entity Relationship Graph</h3></summary>

<br/>

- 🟦 **Person** · 🟩 **Company** · 🟥 **Shell Entity** · 🟨 **Money** · 🟪 **Date** · 🟫 **Location**
- 🔗 Edge types: `signed_by` · `transacted_with` · `subsidiary_of` · `paid_to` · `located_at`
- 🖐️ Interactive pan/zoom on Compose Canvas
- ⚛️ Fruchterman-Reingold physics simulation

</details>

<br/>

<details open>
<summary><h3>💬 Chat-First Interface</h3></summary>

<br/>

| Feature | |
|:--|:--|
| 🎨 Full-screen chat UI | Like Claude / ChatGPT |
| 📎 Multi-image upload | Batch document ingestion |
| ⚡ Streaming responses | Token-by-token with typing indicator |
| 📝 Markdown rendering | **Bold**, bullets, `[Doc: title]` refs |
| 🔒 Session isolation | Each investigation has its own documents |
| 💾 Persistent history | Chat sessions saved in Room DB |

</details>

<br/>

<details open>
<summary><h3>🔎 Hybrid RAG Search</h3></summary>

<br/>

```
  User Query
      │
      ├──▶  📚 FTS4/FTS5 keyword search  ──────── weight: 40%
      │                                              │
      ├──▶  🧮 TF-IDF cosine similarity  ──────── weight: 60%
      │                                              │
      └──▶  🎯 Weighted merge + Top-K  ◀────────────┘
              │
              ▼
        Grounded context → Gemma 4 → Cited answer
```

</details>

<br/>

<details open>
<summary><h3>🌍 Multilingual Support — 20 Languages</h3></summary>

<br/>

<div align="center">

![en](https://img.shields.io/badge/English-4285F4?style=flat-square)
![hi](https://img.shields.io/badge/Hindi-FF9800?style=flat-square)
![ur](https://img.shields.io/badge/Urdu-4CAF50?style=flat-square)
![ta](https://img.shields.io/badge/Tamil-E91E63?style=flat-square)
![te](https://img.shields.io/badge/Telugu-9C27B0?style=flat-square)
![bn](https://img.shields.io/badge/Bengali-00BCD4?style=flat-square)
![mr](https://img.shields.io/badge/Marathi-FF5722?style=flat-square)
![gu](https://img.shields.io/badge/Gujarati-8BC34A?style=flat-square)
![kn](https://img.shields.io/badge/Kannada-3F51B5?style=flat-square)
![ml](https://img.shields.io/badge/Malayalam-009688?style=flat-square)
![pa](https://img.shields.io/badge/Punjabi-FFC107?style=flat-square)
![or](https://img.shields.io/badge/Odia-795548?style=flat-square)
![as](https://img.shields.io/badge/Assamese-607D8B?style=flat-square)
![ne](https://img.shields.io/badge/Nepali-F44336?style=flat-square)
![si](https://img.shields.io/badge/Sinhala-673AB7?style=flat-square)
![ar](https://img.shields.io/badge/Arabic-2196F3?style=flat-square)
![fa](https://img.shields.io/badge/Farsi-CDDC39?style=flat-square)
![ps](https://img.shields.io/badge/Pashto-FF9800?style=flat-square)
![sd](https://img.shields.io/badge/Sindhi-00BCD4?style=flat-square)
![ks](https://img.shields.io/badge/Kashmiri-E91E63?style=flat-square)

</div>

</details>

<br/>

---

<br/>

## 🏗️ Architecture

```
                          ╔══════════════════╗
                          ║   InkVault.ai    ║
                          ╚══════════════════╝

  ┌─────────────────────────────────────────────────────────┐
  │  📱 UI          Chat │ Documents │ Graph │ Timeline     │
  │  🧩 ViewModels  ChatVM  DocsVM    GraphVM  TimelineVM   │
  ├─────────────────────────────────────────────────────────┤
  │  🧠 ML Layer                                            │
  │    ┌─ DocumentProcessingPipeline ────────────────────┐  │
  │    │  ImagePreprocessor → MlKitOCR → GemmaVision     │  │
  │    │  NerExtractor (combined) → ContradictionAnalyzer│  │
  │    │  EmbeddingEngine (TF-IDF) → RagPipeline         │  │
  │    │  InvestigationToolSet (17 tools)                 │  │
  │    │  GemmaInferenceEngine + ModelManager             │  │
  │    └─────────────────────────────────────────────────┘  │
  ├─────────────────────────────────────────────────────────┤
  │  💾 Data Layer                                          │
  │    Room DB: 8 entities │ 7 DAOs │ FTS index             │
  │    5 repositories │ TypeConverters                      │
  ├─────────────────────────────────────────────────────────┤
  │  💉 DI: Hilt (AppModule + DatabaseModule)               │
  │  ⚙️ Config: edgeai_config.json → AppConfig              │
  ├─────────────────────────────────────────────────────────┤
  │  🚀 On-Device Inference                                 │
  │    LiteRT-LM 0.10.0 (Gemma 4, 2.4GB, GPU/CPU)         │
  │    ML Kit OCR 16.0.1 (CPU-only, ~10MB)                 │
  └─────────────────────────────────────────────────────────┘
```

<br/>

---

<br/>

## 🛠️ Tech Stack

<br/>

<table>
<tr>
<td align="center" width="96">
<img src="https://raw.githubusercontent.com/ADevGuide/language-icons/master/icons/kotlin.svg" width="48" height="48" alt="Kotlin" />
<br><sub><b>Kotlin</b></sub>
<br><sub>2.2.10</sub>
</td>
<td align="center" width="96">
<img src="https://3.bp.blogspot.com/-VVp3WvJvl84/X0Ber-QKRYI/AAAAAAAAPMQ/ZAMj0g4F1GQhg36jGA7b4F6gVJRwBZ0TgCLcBGAsYHQ/s1600/jetpack%2Bcompose%2Bicon_RGB.png" width="48" height="48" alt="Compose" />
<br><sub><b>Compose</b></sub>
<br><sub>Material 3</sub>
</td>
<td align="center" width="96">
<img src="https://www.gstatic.com/devrel-devsite/prod/v5ab6fd0ad9c02b131b4d387b5751ac2a3b6b3b3a89ce8a9580d8b3dc39e50f42/android/images/favicon.svg" width="48" height="48" alt="Android" />
<br><sub><b>Android</b></sub>
<br><sub>API 26–35</sub>
</td>
<td align="center" width="96">
<img src="https://www.gstatic.com/devrel-devsite/prod/v5ab6fd0ad9c02b131b4d387b5751ac2a3b6b3b3a89ce8a9580d8b3dc39e50f42/tensorflow/images/favicon.svg" width="48" height="48" alt="LiteRT" />
<br><sub><b>LiteRT-LM</b></sub>
<br><sub>0.10.0</sub>
</td>
<td align="center" width="96">
<img src="https://www.svgrepo.com/show/374016/sqlite.svg" width="48" height="48" alt="Room" />
<br><sub><b>Room</b></sub>
<br><sub>2.7.1</sub>
</td>
<td align="center" width="96">
<img src="https://dagger.dev/images/dagger-logo.png" width="48" height="48" alt="Hilt" />
<br><sub><b>Hilt</b></sub>
<br><sub>2.56.2</sub>
</td>
</tr>
</table>

<br/>

| | Technology | Details |
|:--|:--|:--|
| 🧠 | **Gemma 4 E2B** | 2.4GB quantized, GPU with CPU fallback |
| 🔤 | **ML Kit OCR** | On-device, CPU-only, 20 languages |
| 🔎 | **Hybrid RAG** | FTS keyword + TF-IDF vector, weighted merge |
| 📷 | **CameraX 1.4.1** | Document capture with live preview |
| 🖼️ | **Coil 3.0.4** | Async image loading |
| ⚡ | **Coroutines + Flow** | Reactive async with mutex-based session management |
| ⚙️ | **JSON Config** | All prompts, tools, thresholds externalized |

<br/>

---

<br/>

## 🚀 Quick Start

```bash
# Clone
git clone https://github.com/rickragv/inkvault.ai.git
cd inkvault.ai

# Build
./gradlew assembleDebug

# Deploy Gemma 4 model
adb push gemma-4-E2B-it.litertlm /sdcard/Download/

# Install & launch
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

<br/>

---

<br/>

## ⚙️ Configuration

> All intelligence parameters live in `edgeai_config.json`
> Zero hardcoded strings in application code.

<br/>

| Section | Controls |
|:--|:--|
| 🧠 `model` | temperature, top_k, top_p, max_tokens |
| 🔧 `tools` | 17 tool definitions with JSON schemas |
| 📝 `prompts` | 9 templates: NER, OCR, analysis, chat |
| 🔎 `rag` | chunk size, overlap, FTS/vector weights |
| ⚙️ `processing` | PDF limits, OCR thresholds, contradictions |
| 🎨 `preprocessing` | CLAHE, binarization, contrast |
| 🌍 `languages` | 20 language codes |
| 💻 `ui` | streaming cursor, graph iterations, date format |

<br/>

---

<br/>

<div align="center">

<img src="https://img.shields.io/badge/75-files-1A237E?style=for-the-badge" />
<img src="https://img.shields.io/badge/17-agentic%20tools-4285F4?style=for-the-badge" />
<img src="https://img.shields.io/badge/8-room%20entities-34A853?style=for-the-badge" />
<img src="https://img.shields.io/badge/20-languages-FF8F00?style=for-the-badge" />
<img src="https://img.shields.io/badge/0-network%20calls-E91E63?style=for-the-badge" />

<br/><br/>

**🔐 Built for journalists who can't afford to trust the cloud.**

<br/>

</div>

---

## License

Proprietary. All rights reserved.
