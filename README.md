# Code4j

<p align="center">
  <h2 align="center">Code4j</h2>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-D97757?style=for-the-badge" alt="Java 21" />
  <img src="https://img.shields.io/badge/Code4j-Java%20Edition-B85C3F?style=for-the-badge" alt="Code4j Java Edition" />
  <img src="https://img.shields.io/badge/terminal--first-agent-F0EBE1?style=for-the-badge&labelColor=8B8B8B" alt="terminal-first agent" />
</p>

---

<p align="center">
  A lightweight, local-first, terminal-first coding agent in Java.
</p>

Code4j is built for local development workflows: reading files, searching code, running commands, reviewing edits, keeping resumable sessions, and staying usable in long conversations through context compacting.

## Features

- Anthropic-compatible provider path
- terminal-first coding agent workflow
- built-in tools: file reading, search, edit, write, command execution, `ask_user`, and `load_skill`
- permission review before sensitive actions
- append-only JSONL sessions with `list`, `rename`, `resume`, and `fork`
- manual `/compact` and full autoCompact
- Windows launcher and runnable fat jar

## Build

Requires:

- JDK 21
- Maven 3.9+
- PowerShell

```powershell
cd <code4j source directory>

java -version
mvn -version

mvn test
mvn package
```

Outputs:

```text
target\code4j.jar
target\dist\code4j\
```

## Run

```powershell
cd <your project directory>
java -jar <code4j source directory>\target\code4j.jar

# Or use the launcher
<code4j source directory>\target\dist\code4j\bin\code4j.cmd
```

## Commands

```powershell
code4j
code4j --cwd <path>
code4j --resume <id>
code4j --fork <id>
code4j session list
code4j session rename <id> <title>
code4j --max-steps <n>
code4j --version
code4j --help
```
