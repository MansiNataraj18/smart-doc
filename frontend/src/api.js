// This file talks to your Spring Boot backend. Keeping it separate
// from the components means if the backend URL or shape ever
// changes, this is the only file you need to touch.

const BASE_URL = "http://localhost:8080";

// Calls POST /documents/ask with the user's question and returns
// { answer, sources } -- exactly what AnswerResponse looks like on
// the backend.
//
// selectedDocuments is OPTIONAL. Leave it out (or pass an empty
// array) to search across every uploaded document, exactly like
// before this feature existed. Pass one or more document names to
// restrict retrieval to just those documents -- each name is sent
// as its own "documents" field, matching the backend's
// @RequestParam(value = "documents", required = false) List<String>.
export async function askQuestion(question, selectedDocuments = []) {
  const params = new URLSearchParams();
  params.append("text", question);

  for (const documentName of selectedDocuments) {
    params.append("documents", documentName);
  }

  const response = await fetch(`${BASE_URL}/documents/ask`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: params,
  });

  if (!response.ok) {
    throw new Error(`Server responded with status ${response.status}`);
  }

  return response.json();
}

// Calls POST /documents/upload with one or more PDF files.
// The backend expects them all under the SAME form field name,
// "files" (see @RequestParam("files") MultipartFile[] files),
// so we append each file under that same key rather than giving
// each one a unique key.
//
// "replace" is OPTIONAL and defaults to false. Set it to true only
// after the user has explicitly confirmed (via the "already exists,
// replace it?" dialog) that this upload should replace an existing
// document of the same name -- see UploadPanel.jsx. The backend
// receives this as a "replace" form field.
//
// The backend decides EVERYTHING about each file -- whether it's
// really a PDF, whether it's a duplicate, and whether it succeeded --
// and returns one result per file, e.g.
//   [{ fileName, status: "success" | "duplicate" | "invalid" | "error", message }]
// This function just returns that list as-is; it doesn't inspect or
// judge the files itself.
export async function uploadDocuments(files, replace = false) {
  const formData = new FormData();

  for (const file of files) {
    formData.append("files", file);
  }

  formData.append("replace", replace);

  const response = await fetch(`${BASE_URL}/documents/upload`, {
    method: "POST",
    // No Content-Type header here on purpose -- the browser sets
    // the correct "multipart/form-data; boundary=..." automatically
    // when the body is a FormData object.
    body: formData,
  });

  if (!response.ok) {
    throw new Error(`Server responded with status ${response.status}`);
  }

  return response.json();
}

// Calls GET /documents and returns the persisted list of uploaded
// documents: [{ id, documentName, uploadedAt }, ...]. This is the
// source of truth for "what documents exist" -- it comes from
// PostgreSQL on the backend, not from anything kept in the browser,
// so it's still correct after a page refresh or a restart.
export async function getDocuments() {
  const response = await fetch(`${BASE_URL}/documents`, {
    method: "GET",
  });

  if (!response.ok) {
    throw new Error(`Server responded with status ${response.status}`);
  }

  return response.json();
}

// Calls DELETE /documents?documentName=... to remove an
// already-uploaded document. On the backend this removes BOTH the
// persisted PostgreSQL metadata row AND the document's chunks/vectors
// in Qdrant, so this is a real deletion -- not just a frontend-only
// removal from the list. See UploadPanel.jsx's X button.
export async function deleteDocument(documentName) {
  const params = new URLSearchParams();
  params.append("documentName", documentName);

  const response = await fetch(`${BASE_URL}/documents?${params.toString()}`, {
    method: "DELETE",
  });

  if (!response.ok) {
    throw new Error(`Server responded with status ${response.status}`);
  }

  return response.text();
}
