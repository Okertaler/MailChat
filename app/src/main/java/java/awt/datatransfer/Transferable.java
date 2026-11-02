package java.awt.datatransfer;

/**
 * Minimaler Android-Stub für java.awt.datatransfer.Transferable.
 * JavaMail referenziert dieses Interface indirekt über die Class-Hierarchy.
 */
public interface Transferable {
    DataFlavor[] getTransferDataFlavors();

    boolean isDataFlavorSupported(DataFlavor flavor);

    Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException;
}
