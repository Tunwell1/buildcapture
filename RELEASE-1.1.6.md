Release checklist - Build Capture 1.1.6
========================================

File to upload
--------------

C:\Users\user\Desktop\Projects\BuildCapture\build\libs\buildcapture-1.1.6.jar

Size     185 267 bytes
SHA-256  9F7910ABB43F73CBF78CCBE281BF9FA86A304F2D8F005D72B0491387FDA78CD6
Built    2026-10-27

Minecraft 1.16.5, Forge 36.2.42 or newer, client and server.
Network protocol 7: 1.1.6 clients and servers work with each other. A 1.1.5
client or server on the other side is rejected on join, because this release
removes a field from the box packet; update both sides together. Vanilla
clients still join (they simply do not see the box).


Paste-ready fields
------------------

Name            Build Capture
Version         1.1.6
Loaders         Forge
Game versions   1.16.5
Environment     Client and server
License         All Rights Reserved
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
their own license field). The text is already written for this release.

Changelog (version 1.1.6):

> The box no longer has a per-side size cap. Selection via wheel resize, face
> drags, /capture expand and /capture save now grows as far as the build height
> allows; existing config files that still contain capture.maxSize simply ignore
> it. Very large captures can be heavy on server memory, and the box still
> cannot leave the world. Network protocol bumped to 7, so update the client and
> the server together.

The full text is the 1.1.6 section of changelog.txt if you want the long form.


Steps
-----

1. Modrinth: dashboard -> existing project -> "Create a version" -> file
   buildcapture-1.1.6.jar, version number 1.1.6, name "1.1.6", channel Release,
   loaders Forge, game version 1.16.5, dependencies none, changelog above.
   Publish.
2. CurseForge: author dashboard -> existing project -> "Upload file" with the
   same jar and changelog, release type Release.
3. Verify the uploaded file against the SHA-256 above so the two sites are the
   same binary. Do not rebuild between the two uploads.
4. Tag the commit once master is pushed:

       git tag v1.1.6 && git push --tags


Screenshots
-----------

Ready, all 1920x1080 with the English UI, in upload order:

1. screenshots/01-selection.png - selection box around the winged statue, two sides visible.
2. screenshots/02-face-edit.png - face-edit key held: highlighted face and the corner panel.
3. screenshots/03-browser.png - structure browser with five entries and one selected.
4. screenshots/04-preview.png - placement preview: green footprint box and the translucent ghost.
5. screenshots/05-placed-result.png - optional extra: the placed copy standing next to the original.

Four are enough for the gallery; the fifth proves the placement result. The browser
shot is at night, the other four are in daylight. If a completely uniform set is
wanted later, retake the browser shot at noon.
