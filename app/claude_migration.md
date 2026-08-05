Przewodnik migracji danych z Claude do repozytorium

Cel
- Zautomatyzować konwersję eksportu Claude (czaty, prompty) do Markdown.
- Przygotować import embeddingów do bazy wektorowej (Chroma).

Kroki krótkie
1. Wyeksportuj dane z Claude (ZIP lub folder JSON). Zachowaj strukturę.
2. Umieść plik/rozpakowany katalog w lokalnym środowisku pracy.
3. Uruchom konwersję czatów:
   python app\convert_claude_chats.py --input <path_to_export.zip_or_dir> --out docs\claude-exports
4. Jeśli masz embeddingi (JSON array lub JSONL z {id, vector, metadata}):
   python app\import_embeddings.py --input <embeddings.json> --chroma-dir ./chroma_store

Bezpieczeństwo
- Przejrzyj eksport przed publikacją. Usuń/separuj klucze API, hasła i dane osobowe.

GitHub
- Po weryfikacji dodaj pliki do repo i wypchnij:
  git add docs/claude-exports app/*
  git commit -m "Import: narzędzia migracji Claude"
  gh repo create <org/repo> --public --source=. --push  # lub użyj istniejącego repo

Pomoc
- Jeśli chcesz, wykonam konwersję tutaj — prześlij eksport (ZIP) i udziel dostępu do repozytorium GitHub (token) lub potwierdź tworzenie repo.
