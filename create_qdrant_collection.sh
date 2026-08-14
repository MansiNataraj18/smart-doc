#!/bin/bash
# Run this ONCE to create the "smartdoc_documents" collection in Qdrant.
# Make sure Qdrant is running first: docker compose up -d

curl -X PUT "http://localhost:6333/collections/smartdoc_documents" \
  -H "Content-Type: application/json" \
  -d '{
        "vectors": {
          "size": 1536,
          "distance": "Cosine"
        }
      }'
