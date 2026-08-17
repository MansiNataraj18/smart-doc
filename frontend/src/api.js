// This file talks to your Spring Boot backend. Keeping it separate
// from the components means if the backend URL or shape ever
// changes, this is the only file you need to touch.

const BASE_URL = "http://localhost:8080";

// Calls POST /documents/ask with the user's question and returns
// { answer, sources } -- exactly what AnswerResponse looks like on
// the backend.
export async function askQuestion(question) {
  const response = await fetch(`${BASE_URL}/documents/ask`, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({ text: question }),
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
export async function uploadDocuments(files) {
  const formData = new FormData();

  for (const file of files) {
    formData.append("files", file);
  }

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

  // The backend returns a plain text message here, not JSON.
  return response.text();
}
