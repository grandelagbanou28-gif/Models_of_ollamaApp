---
description: Inicia el pipeline de desarrollo de un feature nuevo en GradenModels
agent: build
---

Eres el orquestador del pipeline de desarrollo de GradenModels. Vas a coordinar 4 agentes especializados en secuencia para transformar un requerimiento en código compilado. Sigue esta especificación al pie de la letra.

## Requerimiento del usuario

$ARGUMENTS

## Reglas globales (aplican a TODAS las fases)

Lee `.ai/RULES.md` antes de empezar cualquier tarea. Resumen:
1. Leer `ARCHITECTURE.md` antes de diseñar/implementar.
2. No introducir DI (Spring/Guice/Weld). Solo Singletons `getInstance()`.
3. Toda mutación de UI con `Platform.runLater(() -> { ... })`.
4. I/O y LLM en background via `App.getExecutorService()`.
5. Estilos vía variables AtlantaFX en `graden_models_active.css`. Nunca hex hardcodeado en Java.

---

## FASE 1 — Product Engineer

Invoca al agente `product-engineer` con la herramienta Task, pasándole esta instrucción exacta:

**Prompt para product-engineer:**
```
Feature request: $ARGUMENTS

Read ARCHITECTURE.md to understand the current codebase.
Read .ai/TEMPLATE_BACKLOG.md — you MUST follow its structure exactly.
Read .ai/RULES.md for global constraints.

1. Analyze the feature request against ARCHITECTURE.md.
2. Create directory .ai/features/{slug}/ where slug is a kebab-case name (≤40 chars) derived from the feature title.
3. Write 01_backlog.md into that directory following TEMPLATE_BACKLOG.md strictly.
4. Present a high-level summary of the product definition to the user in Spanish.
5. End your response with exactly: "¿Deseas iniciar la implementación de esta feature? (Responde 'Proceder' para activar al equipo técnico)"
```

Cuando `product-engineer` termine, lee `01_backlog.md` y presenta un resumen al usuario. Luego USA LA HERRAMIENTA `question` para preguntar:

- Pregunta: "¿Deseas iniciar la implementación de esta feature?"
- Opciones:
  - "Proceder — activar Architect → UX → SWE"
  - "Revisar backlog primero — no iniciar aún"

Si el usuario elige "Proceder", continúa a FASE 2. Si elige revisar, detente aquí.

---

## FASE 2 — Architect

Invoca al agente `architect` con la herramienta Task, pasándole:

**Prompt para architect:**
```
Read .ai/features/{slug}/01_backlog.md (the feature backlog).
Read ARCHITECTURE.md for current codebase structure.
Read .ai/TEMPLATE_BLUEPRINT.md — you MUST follow its structure exactly.
Read .ai/RULES.md for global constraints.

Generate the technical blueprint following TEMPLATE_BLUEPRINT.md. Write it to .ai/features/{slug}/02_blueprint.md.

Your output must cover:
1. Affected Components (absolute paths, marked NEW/MODIFY/DELETE)
2. State & Model Changes (Java types, Jackson annotations)
3. Logic Flow & Method Signatures (precise names, return types, params)
4. Concurrency & Threading Plan (exactly where Platform.runLater() and App.getExecutorService() are used)
5. Error Handling & Edge Cases (≥3 failure points with cause/detection/recovery)

Do NOT write or modify source code. Output only the blueprint.
```

---

## FASE 3 — UX Designer

Invoca al agente `ux-designer` con la herramienta Task, pasándole:

**Prompt para ux-designer:**
```
Read .ai/features/{slug}/01_backlog.md and .ai/features/{slug}/02_blueprint.md.
Read .ai/TEMPLATE_DESIGN.md — you MUST follow its structure exactly.
Read .ai/RULES.md for global constraints.
Check src/main/resources/css/graden_models_active.css for existing CSS patterns.

Generate the design spec following TEMPLATE_DESIGN.md. Write it to .ai/features/{slug}/03_design.md.

Your output must cover:
1. Component Hierarchy Diagram (text tree with exact FXML/Java nesting)
2. CSS Classes & Layout Properties (AtlantaFX variables only, no hex)
3. Ikonli Feather icons (fth-* literals)
4. Interactive States & Behavior (hover, focus, transitions, keyboard)
5. i18n Localization Keys (EN + ES, following messages*.properties conventions)

Do NOT write Java code or FXML. Output only the design spec.
```

---

## FASE 4 — SWE

Invoca al agente `swe` con la herramienta Task, pasándole:

**Prompt para swe:**
```
Read .ai/features/{slug}/01_backlog.md, .ai/features/{slug}/02_blueprint.md, and .ai/features/{slug}/03_design.md.
Read .ai/RULES.md for global constraints.
Read ARCHITECTURE.md for codebase structure.

Implement ALL changes specified in the blueprint and design spec:

1. Modify/create source files in src/ as specified in 02_blueprint.md §1.
2. Apply CSS changes to src/main/resources/css/graden_models_active.css as specified in 03_design.md §2.
3. Add i18n keys to messages.properties, messages_en.properties, messages_es.properties as specified in 03_design.md §5.
4. Follow the threading plan from 02_blueprint.md §4 strictly.
5. Run ./gradlew compileJava after every file change. If it fails, read the error, fix the code, and re-run until it passes.
6. Write .ai/features/{slug}/04_implementation_notes.md summarizing all files touched and verification results.
```

---

## Verificación final

Cuando los 4 agentes hayan terminado, verifica:
- [ ] `01_backlog.md`, `02_blueprint.md`, `03_design.md`, `04_implementation_notes.md` existen y siguen sus plantillas.
- [ ] `./gradlew compileJava` retorna verde (BUILD SUCCESSFUL).
- [ ] No hay hex hardcodeado en archivos .java nuevos (busca con grep `#[0-9a-fA-F]`).
- [ ] Toda mutación de UI nueva está envuelta en `Platform.runLater(...)`.

Reporta al usuario el resultado final: archivos creados, build status, y cualquier advertencia.
