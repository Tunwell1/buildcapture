Release checklist - Build Capture 1.1.5
========================================

File to upload
--------------

C:\Users\user\Desktop\Projects\BuildCapture\build\libs\buildcapture-1.1.5.jar

Size     186 513 bytes
SHA-256  B9371E79AD3E5396F32732F50828D030A29EB8AC53444DD5657401FC618B813E
Built    2026-10-26, committed as f275add

Minecraft 1.16.5, Forge 36.2.42 or newer, client and server.
No protocol change in this release: 1.1.5 clients and servers work with each
other and with 1.1.3/1.1.4 (protocol 6).


Paste-ready fields
------------------

Name            Build Capture
Version         1.1.5
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

Changelog (version 1.1.5):

> Wheel mapping in the placement preview: a plain scroll moves the build five
> blocks, Alt+scroll turns it a quarter turn, Ctrl+scroll changes the height.
> The preview keys and wheel modifiers are real bindings under Controls, so
> they can be changed and given combinations such as Ctrl+R, and the on-screen
> hint prints the keys you have bound. No protocol change.

The full text is the 1.1.5 section of changelog.txt if you want the long form.


Steps
-----

1. Modrinth: modrinth.com -> dashboard -> "Create a project" (first upload
   only). Icon: icon.png. Paste the summary and description above; category
   Utility + Management; environment "Client and server"; license "All Rights
   Reserved".
2. Modrinth: "Create a version" -> file buildcapture-1.1.5.jar, version number
   1.1.5, name "1.1.5", channel Release, loaders Forge, game version 1.16.5,
   dependencies none, changelog above. Publish.
3. CurseForge: curseforge.com -> author dashboard -> "Create project" (first
   upload only). Same name, summary, description, category
   "Server Utility"/"Utility & QoL", game 1.16.5, mod loader Forge, license All
   Rights Reserved. Then "Upload file" with the same jar and changelog, release
   type Release.
4. Tag the commit if you want the store pages and the source to line up:
   git tag v1.1.5 && git push --tags   (only after pushing master).
5. Keep the file and SHA-256 above next to the release notes so the two sites
   are verifiably the same binary. Do not rebuild between the two uploads.


Still missing before the pages look finished
--------------------------------------------

Screenshots. None exist yet; the store copy suggests six shots (STORE.md,
"Screenshots to take"). Four are enough for a decent gallery:

1. selection box around a build, two sides visible;
2. face-edit key held: orange face highlight + corner panel;
3. browser with several entries, sizes and file sizes, one selected;
4. placement preview with the green footprint box and the chat hint.

Take them at 1920x1080 or larger, then upload in that order: the browser and
the preview are the two things that sell the mod.
