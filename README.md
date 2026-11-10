# Free Image to Video: Java SDK

Free image to video AI with no daily cap: start with no sign-up, keep going on a free account. Every video comes with sound and no watermark. This is the official Java SDK for [Free Image to Video](https://freeimagetovideo.ai/?utm_source=maven&utm_medium=package): turn a prompt or a photo into a video clip from Java. Zero dependencies, Java 11+.

```xml
<dependency>
  <groupId>io.github.hanshs474</groupId>
  <artifactId>freeimagetovideo</artifactId>
  <version>0.1.0</version>
</dependency>
```

```java
import io.github.hanshs474.freeimagetovideo.FreeImageToVideo;

FreeImageToVideo fitv = new FreeImageToVideo();   // reads FREEIMAGETOVIDEO_API_KEY
String clip = fitv.generateVideo("a paper lantern drifting over a night river");
String moved = fitv.generateVideo("the camera slowly orbits the statue",
        "https://example.com/statue.jpg", Map.of("duration", 6, "aspect_ratio", "9:16"));
```

Each call submits the run, waits for it and returns the URL of the finished clip. Pass an image URL to [animate a photo](https://freeimagetovideo.ai/video/animate-photo-ai): it becomes the first frame. Leave it null for [free text to video AI](https://freeimagetovideo.ai/video/free-text-to-video-ai) from the prompt alone.

## API key and plans

Create a key at [freeimagetovideo.ai/settings/apikeys](https://freeimagetovideo.ai/settings/apikeys) and set `FREEIMAGETOVIDEO_API_KEY`. API runs need a paid plan: each run draws on the plan's full-speed allowance, then waits in the relaxed queue. See [pricing](https://freeimagetovideo.ai/pricing). To make clips without a key, use the generator on [freeimagetovideo.ai](https://freeimagetovideo.ai/?utm_source=maven&utm_medium=package): start with no sign-up, keep going on a free account.

## Options

| option | values |
|---|---|
| `duration` | 4, 5, 6, 8, 10, 15 (seconds) |
| `resolution` | `480p` (default), `768p` |
| `aspect_ratio` | `16:9` (default), `9:16`, `1:1` |

Model: MiniMax H3 (`minimax-h3`). Every clip comes with sound.

## Lower level

```java
String id = fitv.submit("a koi pond in the rain", null, null);
FreeImageToVideo.Task t = fitv.status(id);   // t.status: pending | processing | success | failed | canceled
String url = fitv.waitFor(id);               // polls every 10 s, gives up after 30 minutes
int left = fitv.credits();
```

## Errors

Every failure is a `FreeImageToVideoException`; `kind()` says what to do next: `AUTH` (no key or a wrong one), `QUOTA` (not enough credits), `PLAN` (API runs need a paid plan), `BUSY` (too many runs at once), `REJECTED` (the content filter refused the prompt, or a cooldown is active), `GENERATION` (the run failed or was canceled; `errorMessage()` says why), `TIMEOUT` (still generating; check later) and `API` (anything else).

MIT licensed.
