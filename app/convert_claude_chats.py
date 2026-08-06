#!/usr/bin/env python3
"""
Konwertuje eksport Claude (ZIP lub rozpakowany katalog JSON) do pojedynczych plików Markdown.
Użycie:
  python app\convert_claude_chats.py --input <export.zip|dir> --out docs\claude-exports
"""
import argparse
import json
import os
import zipfile
import tempfile
from datetime import datetime


def read_json(path):
    with open(path, 'r', encoding='utf-8') as f:
        return json.load(f)


def extract_messages(obj):
    # Próbuj rozpoznać kilka popularnych formatów eksportu czatów
    msgs = []
    if isinstance(obj, list):
        for it in obj:
            # Szukaj roli
            role = it.get('role') or it.get('author') or it.get('sender') or it.get('from')
            # Szukaj treści
            text = None
            if 'content' in it:
                c = it['content']
                if isinstance(c, str):
                    text = c
                elif isinstance(c, list):
                    # lista fragmentów
                    pieces = []
                    for p in c:
                        if isinstance(p, str):
                            pieces.append(p)
                        elif isinstance(p, dict):
                            pieces.append(p.get('text') or '')
                    text = '\n'.join(pieces)
                elif isinstance(c, dict):
                    text = c.get('text') or c.get('content') or str(c)
            else:
                # różne struktury
                text = it.get('text') or it.get('message') or None
            
            # Konwertuj rolę (user -> User, assistant -> Assistant)
            if role:
                role_str = str(role).capitalize()
            else:
                role_str = 'Unknown'
            
            if text:
                msgs.append((role_str, text))
    elif isinstance(obj, dict):
        # common keys: messages, items, conversation, turns
        for key in ('messages', 'items', 'conversation', 'turns', 'utterances'):
            if key in obj and isinstance(obj[key], list):
                return extract_messages(obj[key])
        # sometimes entries are nested in 'data' or 'results'
        for key in ('data', 'results'):
            if key in obj and isinstance(obj[key], list):
                return extract_messages(obj[key])
        # fallback: if dict contains text and author
        if 'author' in obj and 'content' in obj:
            return extract_messages([obj])
        # no messages found
    return msgs


def to_markdown(filename, messages, out_path):
    now = datetime.utcnow().isoformat() + 'Z'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(f"---\nsource: {filename}\nexported_at: {now}\n---\n\n")
        f.write(f"# Konwersacja — {os.path.basename(filename)}\n\n")
        for role, text in messages:
            f.write(f"## {role}\n\n")
            f.write(text.strip() + "\n\n---\n\n")


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--input', '-i', required=True, help='Ścieżka do pliku ZIP lub rozpakowanego katalogu z eksportem')
    p.add_argument('--out', '-o', required=True, help='Katalog wyjściowy na pliki Markdown')
    args = p.parse_args()

    input_path = args.input
    out_dir = args.out
    os.makedirs(out_dir, exist_ok=True)

    work_dir = None
    try:
        if zipfile.is_zipfile(input_path):
            tmp = tempfile.TemporaryDirectory()
            with zipfile.ZipFile(input_path, 'r') as z:
                z.extractall(tmp.name)
            work_dir = tmp.name
        elif os.path.isdir(input_path):
            work_dir = input_path
        elif os.path.isfile(input_path) and input_path.lower().endswith('.json'):
            # single JSON file
            try:
                obj = read_json(input_path)
                messages = extract_messages(obj)
                if messages:
                    base = os.path.splitext(os.path.basename(input_path))[0]
                    out_file = os.path.join(out_dir, base + '.md')
                    to_markdown(input_path, messages, out_file)
                    print('Zapisano', out_file)
                else:
                    print('Brak wiadomości w', input_path)
            except Exception as e:
                print('Błąd przy przetwarzaniu pliku:', e)
            return
        else:
            print('Input nie jest zipem ani katalogiem. Spróbuj rozpakować ZIP ręcznie.')
            return

        for root, _, files in os.walk(work_dir):
            for fn in files:
                if not fn.lower().endswith('.json'):
                    continue
                full = os.path.join(root, fn)
                try:
                    obj = read_json(full)
                except Exception:
                    continue
                messages = extract_messages(obj)
                if not messages:
                    # spróbuj uproszczonego podejścia: jeśli plik jest listą słów/treści
                    continue
                base = os.path.splitext(os.path.relpath(full, work_dir).replace(os.sep, '_'))[0]
                out_file = os.path.join(out_dir, base + '.md')
                to_markdown(full, messages, out_file)
                print('Zapisano', out_file)
    finally:
        # TemporaryDirectory cleanup is automatic when tmp is collected, but ensure explicit if present
        pass

if __name__ == '__main__':
    main()
