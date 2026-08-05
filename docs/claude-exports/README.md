Folder na wyeksportowane konwersacje Claude

Umieść tutaj plik ZIP z eksportem Claude lub rozpakowany katalog z JSONami.

Jak użyć
1. Skopiuj export.zip do tego katalogu lub rozpakuj go tu (zachowaj strukturę).
2. Uruchom w repozytorium:
   python app\convert_claude_chats.py -i docs\claude-exports\export.zip -o docs\claude-exports\md
3. Po konwersji dodaj pliki do commita i wypchnij:
   git add docs/claude-exports/md/*
   git commit -m "Import Claude exports"
   git push

Bezpieczeństwo
- Przed commitem sprawdź, czy nie ma wrażliwych danych (klucze API, hasła). Usuń lub zastąp je przed publikacją.

Jeśli chcesz, mogę sam zaimportować ZIP, jeśli go prześlesz tutaj lub podasz lokalną ścieżkę.