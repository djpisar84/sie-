#!/usr/bin/env python3
r"""
Konwertuje eksport Claude/Cursor (ZIP lub rozpakowany katalog JSON) do pojedynczych plików Markdown.
Użycie:
  python app\convert_claude_chats.py --input <export.zip|dir> --out docs\claude-exports\md
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
    # Format czatów Claude
    msgs = []
    if isinstance(obj, list):
        for it in obj:
            role = it.get('role') or it.get('author') or it.get('sender') or it.get('from')
            text = None
            if 'content' in it:
                c = it['content']
                if isinstance(c, str):
                    text = c
                elif isinstance(c, list):
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
                text = it.get('text') or it.get('message') or None
            
            if role:
                role_str = str(role).capitalize()
            else:
                role_str = 'Unknown'
            
            if text:
                msgs.append((role_str, text))
    elif isinstance(obj, dict):
        for key in ('messages', 'items', 'conversation', 'turns', 'utterances'):
            if key in obj and isinstance(obj[key], list):
                return extract_messages(obj[key])
        for key in ('data', 'results'):
            if key in obj and isinstance(obj[key], list):
                return extract_messages(obj[key])
        if 'author' in obj and 'content' in obj:
            return extract_messages([obj])
    return msgs


def convert_cursor_project(obj):
    # Format projektu Cursor
    if not isinstance(obj, dict):
        return None
    
    project = {
        'name': obj.get('name', 'Unnamed Project'),
        'uuid': obj.get('uuid', ''),
        'description': obj.get('description', ''),
        'created_at': obj.get('created_at', ''),
        'docs': []
    }
    
    if 'docs' in obj and isinstance(obj['docs'], list):
        for doc in obj['docs']:
            project['docs'].append({
                'filename': doc.get('filename', 'untitled'),
                'content': doc.get('content', '')
            })
    
    return project


def to_markdown(filename, messages, out_path):
    now = datetime.utcnow().isoformat() + 'Z'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(f"---\nsource: {filename}\nexported_at: {now}\n---\n\n")
        f.write(f"# Konwersacja — {os.path.basename(filename)}\n\n")
        for role, text in messages:
            f.write(f"## {role}\n\n")
            f.write(text.strip() + "\n\n---\n\n")


def project_to_markdown(project, out_path):
    now = datetime.utcnow().isoformat() + 'Z'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(f"---\nuuid: {project.get('uuid', '')}\nexported_at: {now}\n---\n\n")
        f.write(f"# Projekt: {project['name']}\n\n")
        
        if project.get('description'):
            f.write(f"## Opis\n\n{project['description']}\n\n")
        
        if project.get('created_at'):
            f.write(f"**Utworzony:** {project['created_at']}\n\n")
        
        if project.get('docs'):
            f.write("## Dokumenty\n\n")
            for doc in project['docs']:
                f.write(f"### {doc['filename']}\n\n")
                f.write("```\n")
                f.write(doc['content'])
                f.write("\n```\n\n")


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
            try:
                obj = read_json(input_path)
                # Spróbuj jako projekt Cursor
                project = convert_cursor_project(obj)
                if project and project['name'] != 'Unnamed Project':
                    base = os.path.splitext(os.path.basename(input_path))[0]
                    out_file = os.path.join(out_dir, base + '.md')
                    project_to_markdown(project, out_file)
                    print('Zapisano projekt:', out_file)
                    return
                # Spróbuj jako czaty Claude
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

        projects_converted = 0
        chats_converted = 0

        for root, _, files in os.walk(work_dir):
            for fn in files:
                if not fn.lower().endswith('.json'):
                    continue
                full = os.path.join(root, fn)
                try:
                    obj = read_json(full)
                except Exception:
                    continue
                
                # Próba jako projekt Cursor
                project = convert_cursor_project(obj)
                if project and project['name'] != 'Unnamed Project':
                    base = os.path.splitext(os.path.relpath(full, work_dir).replace(os.sep, '_'))[0]
                    out_file = os.path.join(out_dir, base + '.md')
                    project_to_markdown(project, out_file)
                    print('Projekt:', out_file)
                    projects_converted += 1
                    continue
                
                # Próba jako czaty Claude
                messages = extract_messages(obj)
                if messages:
                    base = os.path.splitext(os.path.relpath(full, work_dir).replace(os.sep, '_'))[0]
                    out_file = os.path.join(out_dir, base + '.md')
                    to_markdown(full, messages, out_file)
                    print('Czat:', out_file)
                    chats_converted += 1

        print(f"\nPodsumowanie: {projects_converted} projektów, {chats_converted} czatów")
    finally:
        pass

if __name__ == '__main__':
    main()

