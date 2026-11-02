package java.awt;

/**
 * Minimaler Android-Stub für java.awt.Toolkit.
 * JavaMail referenziert diese Klasse in seinen Image-Content-Handler.
 */
public class Toolkit {
    private static final Toolkit DEFAULT = new Toolkit();

    public static Toolkit getDefaultToolkit() {
        return DEFAULT;
    }

    public Image createImage(String filename) {
        return null;
    }

    public Image createImage(byte[] data, int size) {
        return null;
    }
}
