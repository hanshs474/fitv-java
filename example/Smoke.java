import io.github.hanshs474.freeimagetovideo.FreeImageToVideo;
import io.github.hanshs474.freeimagetovideo.FreeImageToVideoException;

/**
 * A live check that spends nothing: a submit with a wrong key must land on AUTH.
 * java -cp build/freeimagetovideo-0.1.0.jar example/Smoke.java
 */
public class Smoke {
    public static void main(String[] args) {
        try {
            new FreeImageToVideo("sk-not-a-real-key").submit("a quiet harbor at dawn", null, null);
            System.out.println("wrong key accepted?!");
            System.exit(1);
        } catch (FreeImageToVideoException e) {
            System.out.println("wrong key -> " + e.kind() + " (HTTP " + e.httpStatus() + ")");
            if (e.kind() != FreeImageToVideoException.Kind.AUTH) System.exit(1);
        }
    }
}
