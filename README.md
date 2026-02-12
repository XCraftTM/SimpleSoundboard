# Open Soundboard

**Open Soundboard** is a heavily modified fork of the original [Simple Soundboard](https://github.com/0x1bd/SimpleSoundboard) mod by [kvxd](https://github.com/0x1bd) (aka. 0x1bd).

A feature-rich soundboard mod for [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), providing high-quality audio playback and easy management.

This mod allows you to play `.mp3` files directly into your voice chat stream. It features an in-game GUI to manage sounds, download from YouTube/media sources, and control playback with precision.

## Features

### Audio & Playback
- **MP3 Support**: Play standard `.mp3` files.
- **High Quality Audio**: Uses Cubic (Catmull-Rom) interpolation for smooth resampling and better audio quality.
- **Microphone Injection**: Sounds are merged into your microphone stream, so anyone with Simple Voice Chat can hear them.
- **Dual Volume Sliders**: Independent volume controls for yourself (Local) and other players.
- **Playback Controls**: Play, Pause, Resume, Stop, Seek/Timeline, and Loop individual sounds or all sounds.

### Management & GUI
- **In-Game GUI**: Press **`X`** (default) to open the soundboard.
- **YouTube/Media Downloader**:
    - Built-in downloader using `yt-dlp` (automatically installed).
    - Download audio directly from YouTube or other supported sites by pasting the URL.
    - Progress bar and detailed logs.
    - **Cancel** button to stop downloads in progress.
- **Search & Quick Play**: 
    - Search for sounds by name.
    - Press **Enter** in the search bar to immediately play the top result.
- **Favorites**: Mark sounds as favorites to keep them at the top.

### Keybinds
- **Global Keybinds**:
    - **`X`**: Open Soundboard GUI.
    - **`K`**: Stop all sounds immediately.
- **Per-Sound Keybinds**: Assign specific keys to specific sounds for quick playback without opening the GUI.

## Dependencies

- [Fabric Loader](https://fabricmc.net)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)

*(Note: The mod will automatically download `yt-dlp` and `ffmpeg` locally for the downloader feature)*

## Installation

1. Download and install **Fabric** and **Simple Voice Chat**.
2. Download Open Soundboard.
3. Drop the downloaded jar file into your `mods` folder.
4. Launch Minecraft.

## Usage

1. **Open the GUI:**
    * Press **`X`** in-game to open the Soundboard.
2. **Add Sounds:**
    * Click the **Folder** button to open the local storage directory.
    * Drag `.mp3` files into the `sounds` subdirectory.
    * Or click **YouTube** to download audio from a URL.
3. **Play:**
    * Click "Play" on a sound in the list.
    * Use the sliders at the bottom to adjust volume or seek through the track.
    * Use the "Set Start" button to define a custom starting point for a sound.

## Configuration

Access the configuration via the **Config** button in the soundboard GUI.

*   **Play Locally:** Toggle whether you hear the sounds yourself.
*   **Play While Muted:** Allow playing sounds even if your voice chat microphone is muted.
*   **Sync Audio:** Link local and player volume sliders.
*   **Loop All:** Toggle global looping behavior.

