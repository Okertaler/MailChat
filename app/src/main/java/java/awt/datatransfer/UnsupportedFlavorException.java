package java.awt.datatransfer;

/**
 * Minimaler Android-Stub für java.awt.datatransfer.UnsupportedFlavorException.
 */
public class UnsupportedFlavorException extends Exception {
    private final DataFlavor flavor;

    public UnsupportedFlavorException(DataFlavor flavor) {
        super(flavor != null ? flavor.getMimeType() : null);
        this.flavor = flavor;
    }

    public DataFlavor getFlavor() {
        return flavor;
    }
}
