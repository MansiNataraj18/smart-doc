#!/bin/bash
# Quick test for the /documents/search endpoint.
# Make sure Spring Boot is running first (default port 8080).

curl -X POST "http://localhost:8080/documents/search" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "text=How many days of paid leave do employees get?"
