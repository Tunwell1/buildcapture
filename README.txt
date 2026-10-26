Build Capture
=============

A Minecraft Forge mod for 1.16.5 that saves any part of your world as a normal
datapack structure template. Select two corners, save the box under a name, and
the result can be placed in-world, loaded by a structure block, or shipped as a
datapack. The output is vanilla NBT, so the mod is not needed to *use* a
capture.

Mod id: `buildcapture`.

Requirements
------------
- Minecraft 1.16.5
- Forge 36.2.42 or newer (1.16.x)
- Java 8 (the game itself); the Gradle build uses a newer JDK

Quick start
-----------
1. Aim at a block and run `/capture pos1`.
2. Aim at the opposite corner and run `/capture pos2`.
3. Run `/capture save castle/tower` (the name is free-form).
4. Press `C` (rebindable) to open the structure browser, pick the capture and
   press Place to preview it, then right-click to confirm.

Commands
--------
`/capture` requires permission level 2 (operator).

| Command | Description |
| --- | --- |
| `/capture pos1` / `pos2` | Set a corner to the targeted block (up to 16 blocks away). |
| `/capture expand <direction> [blocks]` | Grow the box on one side (default 1). |
| `/capture shrink <direction> [blocks]` | Shrink it from one side; the box keeps at least one block. |
| `/capture move <direction> [blocks]` | Move the whole box without changing its size. |
| `/capture save <name>` | Save the box as a datapack structure. |
| `/capture place <name> [90\|180\|270]` | Place a capture in front of you. |
| `/capture gui` | Open the structure browser (same as the `C` key). |
| `/capture list` | List captures from the world and the shared library. |
| `/capture undo` | Revert your last save, import or delete. |
| `/capture status` | Show both corners, the box size and the library folder. |
| `/capture clear` | Drop the selection. |
| `/capture reload` | Rescan and enable the capture datapack. |

Directions are `north`, `south`, `east`, `west`, `up` and `down` (north is -Z).
The selection is stored as two corners, so `expand`, `shrink` and `move` keep
corner 1 at the minimum and corner 2 at the maximum of the box. Expanding is
refused if the result would exceed `capture.maxSize` or leave the build height
of the current dimension.

Keys
----
Everything can be rebound under Controls, category "Build Capture".

| Key | Action |
| --- | --- |
| `[` | Set corner 1 at the targeted block (same as `/capture pos1`). |
| `]` | Set corner 2. |
| `Enter` | Save the selection. A small dialog asks for the name, pre-filled with a timestamp: Enter confirms, Esc cancels. |
| `C` | Open the structure browser. |
| `Left Alt` (hold) | Edit the selection, see Interactive editing. |

The three corner/save keys run exactly the same server code as the commands,
so the checks and messages are identical; the chat window is never needed.

Capturing
---------
`/capture save` writes to the world's own datapack:

```
<world>/datapacks/buildcapture_captures/data/<namespace>/structures/<path>.nbt
```

A name without a namespace gets `buildcapture:` (so `castle/tower` becomes
`buildcapture:castle/tower`). Reusing an existing id, such as
`minecraft:village/plains/houses/plains_small_house_1`, overwrites that template
in the datapack and keeps a backup of the previous file.

Entities and jigsaw blocks are included. A selection containing a structure
block is refused, so a capture cannot recursively trigger another structure.

The datapack is created on the first save. `/capture place` uses the in-memory
copy immediately; `/capture reload` (or a restart) enables the datapack for
structure blocks and vanilla commands.

The selection is dropped after a successful save, so the box does not stay on
screen. Set `capture.clearAfterSave` to `false` to keep it for repeated saves
of the same area.

In-game browser
---------------
Press `C` (rebindable under Controls) or run `/capture gui`. The screen lists
every capture from the world and the shared library, with its size and file
weight, and has a search box.

- **Place** starts a placement preview instead of dropping the build at once.
  The structure is drawn as translucent block models inside a green footprint
  box; red means it does not fit inside the build height. Only the nearest
  surface is drawn, so the world behind the structure stays visible. Structures
  over 150,000 blocks fall back to the box alone. Rotate it with the scroll
  wheel (or `R`), use `Ctrl`+scroll to change the height, `Shift`+scroll to move
  it five blocks and the arrow keys for single blocks. Right-click (or Enter)
  places it, middle-click or Escape cancels. Nothing is written until you
  confirm.
- **Import .nbt** opens the system file dialog and uploads the file to the
  server in chunks (the serverbound packet limit is 32 KiB). You can also drop
  one or more `.nbt` files onto the game window. The file is validated on the
  server and written to both the world datapack and the shared library, under a
  free id (`castle.nbt` becomes `buildcapture:castle`, or `buildcapture:castle_2`
  if the name is taken). Only operators can import.
- **Delete** removes the entry from the library and the world datapack, keeping
  a backup. It asks for a second click first.
- **Undo** reverts your last operation: a save goes back to the version it
  replaced, an import is removed, and a delete is restored from its backup. It
  is the same operation as `/capture undo` and is greyed out when there is
  nothing to revert.
- **Open library folder** opens `buildcapture_structures` in the file manager.
  It only works when the server runs on your own machine.

Uploading is capped by `capture.importMaxMb`. Everything the browser does is
checked again on the server: the client never writes files directly.

Interactive editing
-------------------
Hold the face-edit key (left Alt by default; rebind it under Controls). A small
panel appears in the corner with the size, the coordinates and the gestures, so
the controls are visible instead of scrolling away in the chat. It also echoes
the last step ("Moved: up", "Resizing: north").

The box behaves like an object: grab it with the left mouse button and drag. It
slides on the ground plane under the cursor, so it follows the mouse rather than
the camera, and keeps its current height.

- `Ctrl`+wheel moves it up or down, from any view direction.
- The arrow keys nudge it one block, `Page Up` / `Page Down` move it vertically.
- Hold `Shift` for five-block steps.

Resizing needs a face, because a side is the only unambiguous way to say which
end should grow:

- scroll over a face to push that side in or out, `Shift` for five blocks;
- `Shift`+drag a face for a freehand resize.

A face you look at straight on is pushed or pulled by looking up or down while
dragging: pointing at a surface cannot express distance along its normal. The
highlight and the outline stay visible through terrain, so faces behind walls
can be grabbed too. Every step is sent to the server, which clamps it to
`capture.maxSize` and the build height and echoes the result back, so the box
can never end up invalid or outside the world.

The box can also be edited while standing inside it: the face the crosshair
leaves through is the one that gets highlighted.

Shared structure library
------------------------
Every capture is mirrored into a library outside the world
(`buildcapture_structures/` in the game directory by default). The library is
laid out like a datapack, is shared by every world, and can be copied into
`datapacks` or a mod jar as-is.

- `/capture list` marks library-only entries with `*`.
- `/capture place` falls back to the library when the world copy is missing.
- `/capture status` reports the library folder, or that it is disabled.
- Set `capture.libraryDir` to an absolute path to put it elsewhere, or to an
  empty string to disable the library.

Backups and undo
----------------
Before a capture overwrites an existing structure, the old file is copied to:

```
<world>/buildcapture_backups/<world|library>/<namespace>/<path>/
```

The newest 20 backups per structure are kept. `/capture undo` reverts the last
file operation of the player who ran it: a save goes back to the version it
replaced (or is deleted when it was new), an import is removed, and a delete is
restored. The world and library copies are handled together.

Configuration
-------------
`config/buildcapture-common.toml`:

| Option | Default | Meaning |
| --- | --- | --- |
| `capture.maxSize` | 64 | Maximum blocks per side (1-256). |
| `capture.libraryDir` | `buildcapture_structures` | Shared library folder; an empty string disables it. |
| `capture.clearAfterSave` | `true` | Drop the selection after a successful save. |
| `capture.importMaxMb` | 16 | Largest `.nbt` file the browser accepts (1-64 MB). |
| `capture.previewMaxBlocks` | 150 000 | Largest build drawn block by block in the preview (1 000-150 000); bigger builds show only the box. |
| `capture.previewTranslucent` | `true` | Draw the ghost as translucent models; `false` makes it solid. The geometry is cached either way. |

Multiplayer
-----------
The selection lives on the server, per player, and survives a relog. The
commands work with only the server mod installed; the client mod adds the box,
the face highlight, mouse editing, the structure browser and the placement
preview. The ghost block list is sent by the server when the preview starts, so
it also works on multiplayer servers. The client and the server must run the
same mod version (they compare the network protocol on join); a vanilla client
can still join and play, it just does not see the selection box or the ghost.

Building from source
--------------------
```
gradlew.bat build
```

The release jar is written to `build/libs/buildcapture-1.1.2.jar`. The build
targets Java 8; set `JAVA_HOME` to a JDK 17 for Gradle itself. A JDK 8 is
detected automatically. If yours is not, put
`org.gradle.java.installations.paths=<jdk8>,<jdk17>` in
`~/.gradle/gradle.properties` instead of editing the project's file.

Credits and license
-------------------
Author: Tunwell1. See `CREDITS.txt`. All rights reserved; see `LICENSE.txt`.
Minecraft is not affiliated with and does not endorse this mod.
