# SmartDoc

SmartDoc is a document question-answering app. Upload PDFs, then ask questions
about them in a chat interface — the app retrieves the most relevant parts of
your documents and generates an answer, with citations pointing back to the
exact document, page, and chunk it used.

It's a full RAG (Retrieval-Augmented Generation) pipeline: PDF text extraction
→ chunking → embeddings → vector search → answer generation.

## Tech stack

**Backend**
- Java 21, Spring Boot
- LangChain4j (embeddings, chat model, vector store integration)
- OpenAI (`text-embedding-3-small` for embeddings, `gpt-4o-mini` for answers)
- Qdrant — vector database that stores document chunk embeddings
- PostgreSQL — stores document metadata (filename, upload time)
- PDFBox — extracts text from uploaded PDFs

**Frontend**
- React + Vite
- Plain CSS, no UI framework

## How it works

1. **Upload** — a PDF is uploaded, its text is extracted page by page, split
   into overlapping chunks, embedded, and stored in Qdrant. The filename and
   upload time are saved to PostgreSQL.
2. **Ask** — a question is embedded and used to search Qdrant for the most
   relevant chunks (optionally restricted to specific documents you select).
   Those chunks are given to the chat model as context, which generates an
   answer and returns citations for exactly which document/page/chunk it used.
3. **Manage** — the Documents page lists everything uploaded (from
   PostgreSQL, so it survives restarts), and each document can be deleted,
   which removes it from both Qdrant and PostgreSQL.

## Prerequisites

- Java 21+
- Node.js (for the frontend)
- Docker (to run Qdrant + PostgreSQL)
- An OpenAI API key

## Setup

### 1. Environment variables

Copy `.env.example` to `.env` and add your OpenAI API key:

```
OPENAI_API_KEY=<your_openai_api_key>
```

### 2. Start Qdrant and PostgreSQL

```
docker compose up -d
```

This starts:
- Qdrant on `localhost:6333`
- PostgreSQL on `localhost:5433` (database `smartdoc`, user/password `smartdoc`)

### 3. Create the Qdrant collection (first time only)

```
./create_qdrant_collection.sh
```

This creates the `smartdoc_documents` collection that stores chunk embeddings.

### 4. Run the backend

```
./mvnw spring-boot:run
```

The backend starts on `http://localhost:8080`. On startup, Spring Boot
connects to Qdrant and PostgreSQL, and Hibernate creates/updates the
`documents` table automatically (no migration tool is used yet).

### 5. Run the frontend

```
cd frontend
npm install
npm run dev
```

The frontend starts on `http://localhost:5173` and talks to the backend at
`http://localhost:8080`.

## Project structure

```
smart_doc/
├── src/main/java/com/example/smart_doc/
│   ├── config/        Spring bean configuration (OpenAI models, Qdrant, CORS)
│   ├── controller/     DocumentController — the single HTTP entry point
│   ├── model/          Plain data classes (chunks, citations, responses, etc.)
│   ├── repository/     Spring Data JPA repository for document metadata
│   └── service/        Extraction, chunking, embeddings, Qdrant, retrieval, answers
├── frontend/src/
│   ├── components/     UploadPanel, ChatInput, ChatMessage, Sidebar, ConfirmDialog
│   ├── pages/           ChatPage, DocumentsPage
│   └── api.js           All calls to the backend
├── docker-compose.yml   Qdrant + PostgreSQL
└── create_qdrant_collection.sh
```

## API endpoints

| Method | Endpoint                  | Description                                      |
|--------|----------------------------|---------------------------------------------------|
| POST   | `/documents/upload`        | Upload and ingest one or more PDFs                |
| GET    | `/documents`                | List all persisted documents                       |
| DELETE | `/documents`                | Delete a document (Qdrant + PostgreSQL)            |
| POST   | `/documents/ask`            | Ask a question (full RAG pipeline)                 |
| POST   | `/documents/search`         | Debug: search Qdrant without generating an answer |
| POST   | `/documents/test-embedding` | Debug: embed a piece of text                       |

## Notes

- There's no authentication yet — every document is visible to everyone using
  the app.
- The sandbox/CI environment used during development can't reach Maven
  Central, so backend changes are verified by manual review rather than an
  automated build — always run `./mvnw compile` yourself after pulling
  changes.
