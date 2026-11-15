# Architecture

MirrorMax runs offline. A bundled WebView provides the editor controls, and the Android activity coordinates the file picker, playback, export jobs and sharing. Java hosts all Android media and storage operations.

| Path | Responsibility |
| --- | --- |
| `assets/` | Editor markup, styles, controls and Desert Dusk sample |
| `res/` | Android theme and launcher artwork |
| `MainActivity.java` | Android activity, lifecycle, picker and local editor bridge |
| `Source.java` | Import inspection, dimensions, rotation and media metadata |
| `Mirror.java` and `Gl.java` | Shared fold rules and OpenGL frame rendering |
| `Preview.java` | Live image, GIF and video preview |
| `Gif.java` | Streaming GIF composition, disposal and encoding |
| `Encode.java` and `Audio.java` | H.264 MP4 output, audio preservation and AAC conversion |
| `Exports.java` | PNG/GIF export, public Downloads publishing and export discovery |
| `test/` | Core and Android integration tests, fixtures and release evidence |

Each axis is folded independently. Rendering looks up the output pixel through the second mirror pass first, then through the first pass to fetch the source pixel. PNG and GIF use the integer mapping, while the preview and MP4 renderer apply the matching fold in OpenGL. Frame timing is preserved for video and animated GIF inputs. MP4 export uses the available Android H.264 encoder. Compatible AAC audio is copied, and other supported audio is converted to AAC when requested.

Imports are copied into the app's cache so the media can be read consistently. Exports are first written to a temporary file and published through MediaStore only when complete. Originals are not edited.
