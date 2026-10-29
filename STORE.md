# Build Capture - store copy (1.1.7)

Ready-to-paste text for Modrinth and CurseForge. Keep it in sync with the
released jar.

## Metadata

| Field | Value |
| --- | --- |
| Name | Build Capture |
| Version | 1.1.7 |
| Minecraft | 1.16.5 |
| Loader | Forge 36.2.42 or newer (1.16.x) |
| License | MIT |
| Environment | Client and server |
| Modrinth categories | Utility, Management |
| CurseForge categories | Server Utility, Utility & QoL |
| File | `buildcapture-1.1.7.jar` |
| Mod id | `buildcapture` |

Short summary (Modrinth / CurseForge "Summary", under 256 characters):

> Select any build and save it as a standard datapack structure. Drag the box
> with the mouse, preview placements, import .nbt files in-game and keep a
> library shared by every world — no WorldEdit and no proprietary format.

---

## Description

### Turn a build into a datapack structure

Build Capture saves any part of your world as a normal Minecraft structure
template. Select two corners, run `/capture save castle/tower`, and the build is
written to `world/datapacks/buildcapture_captures/`. Use it immediately with
`/capture place`, or run `/capture reload` to make it available to structure
blocks and vanilla commands. The output is vanilla NBT with entities and jigsaw
blocks preserved, so the mod is not needed to *use* a capture.

It is a small, focused tool: no schematic format, no external editor, no
dependency on WorldEdit.

### Quick start

1. Install the mod on a Forge 1.16.5 server (and on your client if you want the
   visible box and mouse editing).
2. Aim at a block and run `/capture pos1`. Aim at the opposite corner and run
   `/capture pos2`.
3. Run `/capture save mybase/tower`. The name is free-form: a namespace is added
   automatically, and `castle/tower` keeps its folder.
4. Press `C` (rebindable) or run `/capture gui` to open the browser, pick the
   capture and press Place to preview it in the world; right-click confirms.
   `/capture reload` makes the datapack available to structure blocks.

### Editing the box

Commands resize the selection without reselecting the corners:

- `/capture expand <direction> [blocks]`
- `/capture shrink <direction> [blocks]`
- `/capture move <direction> [blocks]`

Directions are `north`, `south`, `east`, `west`, `up` and `down`; the step
defaults to one block. The box always keeps at least one block and stays inside
the build height.

With the client mod installed you can also edit the box directly. Hold the
face-edit key (left Alt by default; rebindable under Controls) and a small panel
shows the size, the coordinates, the gestures and the last step. The box behaves
like an object:

- drag it with the left mouse button: it slides on the ground plane under the
  cursor and keeps its height;
- `Ctrl`+wheel moves it up or down (rebindable, like every wheel modifier);
- the arrow keys nudge it one block, `Page Up` / `Page Down` move it vertically;
- `Shift` gives five-block steps.

Resizing needs a face: scroll over it to push that side in or out, or `Shift`+
drag the face for a freehand resize. The face under the crosshair is highlighted,
through terrain if necessary.

When you look at a face almost straight on, the crosshair cannot express depth,
so the face is pushed or pulled by moving the mouse up and down while dragging.
Every step is sent to the server, which clamps it to the build height, so the
selection cannot end up invalid or outside the world.

The selection is dropped after a successful save, so the box does not stay on
screen. Set `capture.clearAfterSave` to `false` to keep it for saving the same
area under several names.

### In-game browser

Press `C` or run `/capture gui`. The screen lists every capture from the world
datapack and the shared library with its dimensions and file size, has a search
box, and marks world-only, library-only and mirrored entries with a coloured dot.

**Place** does not drop the build immediately: the structure is shown as
translucent block models inside a green footprint box at the target spot (red
when it does not fit the build height), and only the nearest surface is drawn,
so you can still see the world behind it. Structures over 150,000 blocks fall
back to the box alone. A plain scroll moves it five blocks, `Alt`+scroll turns it
a quarter turn, `Ctrl`+scroll changes the height and the arrow keys move it a
block. Right-click (or Enter) places it; middle-click or Escape cancels. Nothing
is written until you confirm, and the server re-checks the id, distance and
build height.

**Import .nbt** opens the system file dialog. You can also drop one or more
`.nbt` files onto the game window. The client uploads the file to the server in
chunks (the serverbound packet limit is 32 KiB), the server validates it as a
structure and writes it to both the world datapack and the shared library under
a free id: `castle.nbt` becomes `buildcapture:castle`, or `buildcapture:castle_2`
if that name is taken. Files saved by a newer Minecraft are accepted with a
DataVersion warning. Only operators can import.

**Delete** removes the entry from both places, keeping a backup, and asks for a
second click first. **Undo** reverts your last operation — a save goes back to
the version it replaced, an import is removed, a delete is restored — and is
disabled when there is nothing to revert. **Open library folder** opens
`buildcapture_structures` in the file manager — only meaningful when the server
runs on your own machine.

### Library, backups and undo

Every capture is written twice: into the world datapack, and into a shared
library in the game directory (`buildcapture_structures/` by default, config
`capture.libraryDir`). The library is a valid datapack too and is visible from
every world, which makes it a practical place to keep a personal kit of parts.
`/capture list` marks library-only entries with `*`, and `/capture place` falls
back to the library when the world copy does not exist.

Overwriting an existing structure never destroys it: up to 20 previous versions
are kept under `world/buildcapture_backups/`, and `/capture undo` reverts your
last file operation: a save is restored to its previous version, an import is
removed, a delete is restored. The world and library copies are handled
together.

### Commands

`/capture` requires permission level 2 (operator).

| Command | Description |
| --- | --- |
| `/capture pos1` / `pos2` | Set a corner to the targeted block (up to 16 blocks away). |
| `/capture expand <dir> [n]` | Grow the box on one side. |
| `/capture shrink <dir> [n]` | Shrink it from one side. |
| `/capture move <dir> [n]` | Move the whole box without changing its size. |
| `/capture save <name>` | Save the box as a datapack structure. |
| `/capture place <name> [90\|180\|270]` | Place a captured structure in front of you. |
| `/capture gui` | Open the structure browser (same as the `C` key). |
| `/capture list` | List captures from the world and the library. |
| `/capture undo` | Revert your last save, import or delete. |
| `/capture status` | Show both corners, the box size and the library folder. |
| `/capture clear` | Drop the selection. |
| `/capture reload` | Rescan and enable the capture datapack. |

### Keys

Everything is rebindable under Controls, category "Build Capture". The corner
and save keys run the same server code as the commands, so the chat window is
not needed for the normal flow.

| Key | Action |
| --- | --- |
| `[` / `]` | Set corner 1 / 2 at the targeted block. |
| `Enter` | Save the selection; the name dialog is pre-filled with a timestamp. Places the preview while one is active. |
| `C` | Open the structure browser. |
| `Left Alt` (hold) | Edit the selection: move with the wheel, resize by dragging a face. Turns the placement preview with the wheel. |
| `R` | Turn the placement preview. |
| Arrows / `Page Up` / `Page Down` | Move the preview one block. |
| `Left Ctrl` (hold) + wheel | Change the preview's height. |

Every one of these can be rebound, including key combinations (for example
`Ctrl+R`): Forge records the modifier held when the new key is pressed. The
scroll wheel itself cannot be bound by Minecraft, so the preview uses the edit
key and the height key to decide what a scroll means; a plain scroll moves the
build five blocks.

### Configuration

| Option | Default | Meaning |
| --- | --- | --- |
| `capture.libraryDir` | `buildcapture_structures` | Shared library folder; empty string disables it. |
| `capture.clearAfterSave` | `true` | Drop the selection after a successful save. |
| `capture.importMaxMb` | 16 | Largest `.nbt` file the browser accepts (1–64 MB). |
| `capture.previewMaxBlocks` | `150000` | Largest preview drawn block by block (1000–150000). |
| `capture.previewTranslucent` | `true` | Draw the preview as translucent block models; off is solid. |

### Multiplayer and permissions

The selection lives on the server, per player, and survives relogging. The
commands work with only the server mod installed; the client mod adds the
outline, the face highlight, mouse editing, the browser and the placement
preview. The ghost block list is sent by the server when the preview starts, so
it also works on multiplayer servers. Imports are uploaded to the server and
checked there, so they work on multiplayer servers — the client never writes
files directly. Players without the mod can join a server that has it and play
normally — they just do not see the box or the browser.

### Compatibility and limits

- Minecraft 1.16.5, Forge 36.2.42+.
- A 1.20.1 version is not available yet.
- The box is limited only by the build height; there is no per-side cap.
- Imported files are capped by `capture.importMaxMb`; there is no per-side cap.
  The preview only ghosts structures up to 1024 blocks per axis or 150000
  blocks; larger ones still place, with just the footprint box shown.
- A capture containing a structure block is refused, to avoid recursive
  structure placement.
- `/capture undo` only covers the last save, import or delete of the player who
  ran it.

### License

MIT. Minecraft is not affiliated with and does not endorse this
mod.

---

## Screenshots (1920×1080, ready)

1. `screenshots/01-selection.png` — the translucent box around the winged statue.
2. `screenshots/02-face-edit.png` — face-edit key held: highlighted face and corner panel.
3. `screenshots/03-browser.png` — the structure browser with five entries, one selected.
4. `screenshots/04-preview.png` — placement preview: green footprint box and ghost.
5. `screenshots/05-placed-result.png` — optional: the placed copy next to the original.
