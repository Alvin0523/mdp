---
icon: lucide/git-branch
---

# Dev Workflow & OpenSpec Setup

Day-to-day dev loop across submodules (`mdp_ros` and `mdp_stm32`). OpenSpec (below) is an **optional**
spec-driven workflow for AI-assisted changes — skip this whole page if you're not using it.

---

## 1. Initial OpenSpec Setup (Optional)

`openspec/` folders inside submodules are already committed — never create them manually. Only `.claude/` or tool-specific folders (gitignored) need to be generated per machine.

### Step 1: Install CLI (Once per machine)

```bash
pixi global install nodejs
npm install -g @fission-ai/openspec@latest
```

### Step 2: Set up AI tool (Once per submodule, per machine)

```bash
cd mdp_ros        # or mdp_stm32
openspec init
```

Select your AI tool (e.g., Claude Code, Cursor, Antigravity) and restart your IDE. 
- Run `openspec update` after upgrading the CLI to refresh tool settings.

### Step 3: Workspace Root Requirement
To use `/opsx:*` slash commands, open the specific submodule directory as your workspace root (e.g., `code mdp_ros`), not the top-level `mdp/` monorepo directory.

---

## 2. OpenSpec Concepts & Structure

```text
openspec/
├── config.yaml                        Project-wide config (schema, context, rules)
├── specs/                             Merged current system specification (updated by archive)
│   └── <capability>/
│       └── spec.md
└── changes/
    ├── <change-name>/                 In-flight proposal
    │   ├── proposal.md                Why / What Changes / Capabilities / Impact
    │   ├── design.md                  Architecture context / Decisions (optional)
    │   ├── tasks.md                   Implementation checklist (used by apply)
    │   └── specs/<capability>/spec.md Delta spec (ADDED/MODIFIED/REMOVED requirements)
    └── archive/                       Completed & archived proposals
```

- **`openspec/specs/`**: System source of truth. Populated automatically when a change is archived.
- **`openspec/changes/<name>/`**: Contains in-flight requirements and checklists.

### Auto-Context (`config.yaml`)
To automatically include system architecture in proposals, ensure `openspec/config.yaml` includes:

```yaml
context: |
  Read ../docs/index.md before proposing anything.
```

---

## 3. Day-to-Day Development Loop

```mermaid
flowchart TD
  A[Branch off main] --> X{{"/opsx:explore (optional)<br/>unfamiliar reference material?"}}
  X --> B["/opsx:propose"]
  B --> C[Review proposal/spec/tasks]
  C --> D["/opsx:apply"]
  D --> E[Test implementation]
  E --> F[Commit implementation]
  F --> G["/opsx:archive"]
  G --> H[Commit archived change]
  H --> I[Push & open PR]
```
<p align="center"><strong>Fig. 1</strong> — Day-to-Day Development Loop</p>

!!! note "OpenSpec Git Independence"
    OpenSpec commands (`propose`, `apply`, `archive`) only create/edit files. Branching, committing, and pushing are standard git operations.

---

## 4. OpenSpec Commands Reference

=== "Slash Commands (Recommended)"

    | Stage | Command | Description |
    |---|---|---|
    | Explore | `/opsx:explore` | Interactive discussion to clarify requirements |
    | Propose | `/opsx:propose "<description>"` | Generates `proposal.md`, spec deltas, `design.md`, and `tasks.md` |
    | Apply | `/opsx:apply` | Implements the task checklist |
    | Archive | `/opsx:archive` | Moves change to `archive/` and updates system specs |

=== "CLI Commands"

    | Stage | Command | Description |
    |---|---|---|
    | Propose | `openspec new change <name>` | Scaffolds change files to fill in manually |
    | Validate | `openspec change validate <name>` | Validates change formatting |
    | Archive | `openspec archive <name>` | Moves change to `archive/` and updates system specs |

---

## 5. Branch Naming & Commit Guidelines

### Branch Format
```text
<your-name>/<change-name>
```
*Example*: `alvin/add-mdp-description`, `jess/fix-steering-servo-pwm`

| Prefix | Meaning |
|---|---|
| `add-...` | New feature or capability |
| `fix-...` | Bug fix |
| `refactor-...` | Code refactoring with no behavior change |

### Commit Rules

| Event | Action |
|---|---|
| After `/opsx:apply` & tested | Commit code + change files together (`git commit -m "Add feature X"`) |
| After `/opsx:archive` | Commit archive file movement separately (`git commit -m "Archive add-feature-x"`) |

---

## 6. Writing Effective Proposals

When issuing `/opsx:propose`, include 3 key elements:
1. **Target TODO**: Reference the exact TODO item from [`docs/index.md`](index.md).
2. **Implementation Patterns**: Reference exact file paths or design patterns.
3. **Explicit Scope Boundaries**: State what to *exclude* to keep changes incremental.

```text title="Example Propose Message"
/opsx:propose "Integrate micro-ROS transport into mdp_stm32 firmware, replacing
the current zenoh-pico demo. Scope: transport + rclc node wiring only.
Do not touch motor.c or encoders — those are separate TODO items."
```

---

## 7. Troubleshooting & Common Fixes

Real issues encountered during development and their verified resolutions:

### :gear: OpenSpec CLI Issues

??? question "`openspec update --tools claude` fails: `error: unknown option '--tools'`"

    That flag was removed in a CLI update. Tool selection is now an interactive picker on both `openspec init` and `openspec update` — run the command with no flag and pick from the list.

??? question "Not sure whether to run `init` or `update`"

    | Command | Use it for |
    | --- | --- |
    | `openspec init` | First time setting up your local `.claude/` adapter (new machine, new teammate, fresh clone). Safe to re-run; never touches `config.yaml`/`specs/`/`changes/`. |
    | `openspec update` | Refreshing an adapter you *already* have, after upgrading the CLI. Can't create one from scratch. |

For git/submodule issues, see [Troubleshooting](troubleshooting.md#git-submodules).