@echo off
echo ========================================================
echo Starting ChromaDB Vector Database on port 8000...
echo Data directory: ./chroma_data
echo ========================================================
chroma run --path ./chroma_data --port 8000
pause
