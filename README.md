# Graden Models

A modern, native desktop client for [Ollama](https://ollama.com/), built with JavaFX. Graden Models provides a beautiful, user-friendly interface to manage your local LLMs and chat with them, featuring a sleek AtlantaFX-inspired design.

## Features

- **Model Management**: Browse the Ollama library, download models with real-time progress, view installed models and uninstall them
- **Chat Interface**: Multiple chat sessions, pin/rename/delete chats, clean message bubbles for user and AI
- **RAG (Retrieval-Augmented Generation)**: Add documents to a local RAG library and query them during chat
- **Multi-modal**: Attach images to chat messages for compatible vision models
- **Theming**: Full Light/Dark mode support with AtlantaFX styles

## Tech Stack

- Java 17+ with JavaFX 17
- Gradle 8.5 build system
- AtlantaFX for modern UI theming
- LangChain4j for RAG pipeline
- Jackson for JSON serialization
- Flexmark for Markdown rendering

## Getting Started

### Prerequisites

- Java 17 or higher
- [Ollama](https://ollama.com/) installed and running locally

### Download

Download the latest release from the [Releases page](../../releases).

### Run from source

```bash
git clone https://github.com/gradenmodels/GradenModels.git
cd GradenModels
./gradlew run
```

### Build distribution

```bash
./gradlew packageDistribution
```

## License

Distributed under the MIT License. See `LICENSE` for more information.
# Models_of_ollamaApp
# Models_of_ollamaApp
