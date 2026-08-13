---
icon: lucide/wrench
---

# Troubleshooting

Real gotchas we've actually hit, so nobody has to rediscover them. Click a title to expand.

## :gear: OpenSpec

??? question "`openspec update --tools claude` fails: `error: unknown option '--tools'`"

    That flag was removed in a CLI update. Tool selection is now an interactive picker on both `openspec init` and `openspec update` — run the command with no flag and pick from the list. See [Dev Workflow & OpenSpec Setup](dev_workflow.md).

??? question "Not sure whether to run `init` or `update`"

    | Command | Use it for |
    | --- | --- |
    | `openspec init` | First time setting up your local `.claude/` adapter (new machine, new teammate, fresh clone). Safe to re-run; never touches `config.yaml`/`specs/`/`changes/`. |
    | `openspec update` | Refreshing an adapter you *already* have, after upgrading the CLI. Can't create one from scratch. |

??? question "`npm install -g @fission-ai/openspec@latest` warns about `allow-scripts`"

    ```
    npm warn allow-scripts 1 package has install scripts not yet covered by allowScripts
    ```

    npm blocking the package's `postinstall` script by default — a safety feature, not an error.

    !!! tip
        Usually harmless, try `openspec` first. Only run the suggested `--allow-scripts` command if something's actually missing/broken afterward.

??? question "Forgot to check `.gitignore` before trying a new AI tool"

    Setting up a tool other than Claude Code (Cursor, Codex, ...) for the first time? Its generated folder might not be in `.gitignore` yet. Check `mdp_ros/.gitignore` / `mdp_stm32/.gitignore` and add the folder name before your next `git add`.

## :twisted_rightwards_arrows: Git / submodules

??? question "Cloned the repo but `mdp_ros/`/`mdp_stm32/` are empty"

    Need `--recurse-submodules` on clone, or afterward:

    ```bash
    pixi run clone-all
    ```

    See [index](index.md#one-time-setup).
