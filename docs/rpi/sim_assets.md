---
icon: lucide/image
---

# Simulation Symbol Assets

How the MDP symbol images get onto the obstacle faces in Gazebo, and how to
regenerate them.

---

## What Gazebo actually needs

Three files per symbol, split by kind under `mdp_description/models/symbols/`:

```
models/symbols/
├── panels/     <stem>.obj + <stem>.mtl   # geometry (31 × 2 files, ~250KB)
└── textures/   <stem>.png                # images   (31 files, ~9.7MB)
```

The world SDF names **only the `.obj`**. The image is reached indirectly:

```
task1_arena.sdf  →  panels/11_One.obj  →  panels/11_One.mtl  →  ../textures/11_One.png
   <uri>              mtllib                  map_Kd
```

So a PNG can be swapped with no SDF change at all, as long as the filename stays
the same.

!!! warning "Why a mesh, and not just a texture on a box"
    Gazebo Harmonic (ogre2) will not reliably render an `<albedo_map>` on a
    primitive `<box>`/`<plane>` — it comes out blank or black. A UV-mapped mesh
    with a material that names the texture is the supported route, hence the
    two-triangle quad per symbol.

    Related constraint: obstacles must exist **at world load**. Spawned
    afterwards, the decal renders black because the texture never binds. That is
    why they are baked into `task1_arena.sdf` rather than spawned at runtime.

### Panel quad geometry

`panels/*.obj` is a `6.1cm × 6.1cm` square (matching the real printed scannable
image), in the **X-Z plane with its normal on +Y** — i.e. standing upright and
facing North by default, with image-up already on world +Z.

Two consequences when placing one in the SDF:

- **`<scale>` must be `1 1 1`.** The quad is already at real size. Do not scale.
- **Rotation is yaw only** (`roll=0, pitch=0`):

    | Facing | Yaw |
    | --- | --- |
    | N | `0` |
    | E | `-π/2` |
    | S | `π` |
    | W | `+π/2` |

Because the quad is pre-oriented and yaw turns about the vertical axis, the
image's "up" stays world-up on every face. A rotation that merely aims the
normal outward leaves a free spin about that normal — which is what previously
left symbols upright on the South face only (E 90° clockwise, W 90°
anticlockwise, N 180°).

See the block comment above the obstacle models in `task1_arena.sdf` for the
placement numbers (face offset, flush-to-top height).

---

## Regenerating

### Panel meshes — after adding or removing a symbol PNG

```bash
pixi run panels
```

Runs `mdp_description/scripts/gen_symbol_meshes.py`, which scans
`models/symbols/textures/` for `*.png` and writes a matching `.obj` + `.mtl`
pair into `models/symbols/panels/` for each one. Idempotent — safe to re-run,
and it overwrites rather than skipping, so stale pairs get refreshed.

Nothing invokes this automatically, including the build. Run it by hand whenever
the set of PNGs changes.

### Textures — importing new source images

```bash
pixi run import-symbols /path/to/source/images
```

Runs `mdp_description/scripts/import_symbol_textures.py`, which converts every
`*.png` in the source directory to `512×512` RGB and writes it into
`models/symbols/textures/`. Then run `pixi run panels`.

Why the conversion is not just a copy:

- **Downscale to 512².** Source crops are ~2000-2500² and vary per file; a
  uniform power-of-two square is what you want for a GPU texture. LANCZOS
  resampling, because these are hard-edged symbols on flat backgrounds and
  cheaper filters visibly alias the edges.
- **RGBA → RGB.** The source crops carry an alpha channel that is fully opaque,
  so it is a wasted fourth channel.

### Full pipeline

```
source crops (~2200² RGBA)          # kept outside the repo
        │  pixi run import-symbols <dir>
        ▼
models/symbols/textures/*.png       # 512² RGB
        │  pixi run panels
        ▼
models/symbols/panels/*.obj + .mtl
        │  referenced by <uri>
        ▼
worlds/task1_arena.sdf              # hand-maintained, single source of truth
```

!!! note "The repo keeps only the 512² textures"
    There is deliberately no higher-resolution master in the repo. If you need
    to regenerate at a different size, you need the original source crops —
    keep them somewhere safe outside the tree.

---

## Changing which symbol is on which obstacle

Edit the `<uri>` in `mdp_description/worlds/task1_arena.sdf`. That file is
hand-maintained and is the single source of truth for the sim.

`mdp_bringup/config/test_obstacles.yaml` does **not** drive the sim — it feeds
only the planner, via `publish_test_obstacles.py` → `/obstacle_setup`, and only
its `id`/`x`/`y`/`facing` fields. It carries a comment listing which symbol is on
which obstacle purely as a cross-reference; keep it in step by hand.

!!! warning "Two places, one layout"
    The obstacle positions in `task1_arena.sdf` and in `test_obstacles.yaml` are
    no longer generated from a common source. If they drift, the planner and the
    simulator disagree about where the obstacles are — which presents as a
    planning or localisation bug rather than a stale config. Edit both together.

---

## Texture paths are portable

`<uri>` uses `model://`, resolved through `GZ_SIM_RESOURCE_PATH`, which the sim
launches point at `<install>/mdp_description/share`:

```xml
<uri>model://mdp_description/models/symbols/panels/11_One.obj</uri>
```

`package://` would resolve identically here — same mechanism, and it is what the
URDF meshes use. `model://` is the SDF-native scheme, which is why the world file
uses it.

These were absolute `file:///home/...` paths until 2026-09-11, which pinned the
world to one machine's install tree.

Because the workspace is built with `colcon build --symlink-install`, every file
under `install/.../models/` is a symlink back into `src/`. Editing a PNG in `src`
takes effect with **no rebuild**. Adding a *new* file still needs a build, so the
symlink gets created.

---

## Directory naming

`models/symbols/` is not a Gazebo *model* directory in the strict sense — a
proper one carries a `model.config` plus `model.sdf` and is discovered by name.
This is just asset storage for meshes and images, so by ROS convention it would
sit under `meshes/` alongside the robot's STLs.

It works as-is because `model://mdp_description/...` is being used as a package
resource root rather than a named-model lookup, and `GZ_SIM_RESOURCE_PATH` points
at the package's parent share directory. Renaming would mean updating the `<uri>`
paths in `task1_arena.sdf`, the `install(DIRECTORY ...)` list in
`mdp_description/CMakeLists.txt`, and the two generator scripts — worth doing for
tidiness, not for correctness.
