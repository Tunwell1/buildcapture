Release checklist - Build Capture 1.1.7
========================================

File to upload
--------------

C:\Users\user\Desktop\Projects\BuildCapture\build\libs\buildcapture-1.1.7.jar

Size     198 354 bytes
SHA-256  BE30D3E8E00A44066624DD61918910E77248003B14AE0E7770AC60C8E4312C2E
Built    2026-10-29

Minecraft 1.16.5, Forge 36.2.42 or newer, client and server.
Network protocol is still 7: no packet layout changed, so 1.1.6 and 1.1.7
clients and servers work with each other in either combination. Vanilla clients
still join and play; they just do not see the box.


Paste-ready fields
------------------

Name            Build Capture
Version         1.1.7
Loaders         Forge
Game versions   1.16.5
Environment     Client and server
License         MIT
Mod id          buildcapture
Categories      Modrinth: Utility, Management
                CurseForge: Server Utility, Utility & QoL

Summary (Modrinth / CurseForge, under 256 characters):

> Select any build and save it as a standard datapack structure. Drag the box
> with the mouse, preview placements, import .nbt files in-game and keep a
> library shared by every world - no WorldEdit and no proprietary format.

Description:

Paste STORE.md from "### Turn a build into a datapack structure" through the end
of "### Compatibility and limits" (stop before "### License"; both sites have
their own license field). Only the import-limit paragraph changed.

Changelog (version 1.1.7):

> Polish and safety pass. The 512-blocks-per-side import limit is gone, and
> imported files are validated through the template loader before they are
> written; oversized or decompression-bomb files are rejected and stalled
> uploads time out. Structure names with "." or ".." segments are refused, and
> saves or deletes abort when the existing file cannot be backed up, so
> undo is never lost; files are now written atomically. Changing dimension
> clears the selection, /capture place shares the browser's build-height and
> distance checks, and the prefilled save name includes seconds. The browser
> no longer crashes on a foreign-OS library path and clears its list on
> disconnect. Forge clients without the mod can join again (the missing
> channel was rejected before) and receive command feedback as plain English
> instead of raw translation keys. Network protocol is unchanged (7), so 1.1.6
> and 1.1.7 work together.

The full text is the 1.1.7 section of changelog.txt if you want the long form.


Steps
-----

1. Modrinth: the project still is not public. Submit it for review first if
   that was not done yet; after approval, dashboard -> "Create a version" ->
   file buildcapture-1.1.7.jar, version 1.1.7, channel Release, loaders Forge,
   game version 1.16.5, dependencies none, changelog above. Publish.
2. CurseForge: author dashboard -> existing project -> "Upload file" with the
   same jar and changelog, release type Release.
3. Verify the uploaded file against the SHA-256 above so the two sites hold the
   same binary. Do not rebuild between the two uploads.
4. Tag the commit once master is pushed:

       git tag v1.1.7 && git push --tags


Screenshots
-----------

No new screenshots: 1.1.7 changes behavior and safety, not the look. The five
existing shots in the gallery still describe the mod. The import section of the
description no longer promises a 512-block limit, which is the only store text
change.
