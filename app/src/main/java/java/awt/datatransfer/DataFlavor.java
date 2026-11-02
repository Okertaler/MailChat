package java.awt.datatransfer;

/**
 * Minimaler Android-Stub für java.awt.datatransfer.DataFlavor.
 *
 * JavaMail (com.sun.mail) + javax.activation referenzieren diese Klasse in ihren
 * Content-Handler (handler_base, multipart_mixed, text_plain) über
 * javax.activation.ActivationDataFlavor, die diesen Stub erbt.
 *
 * Auf Android wird AWT nie ausgeführt — der Stub erfüllt nur den Class-Loader
 * und die von ActivationDataFlavor aufgerufenen Methoden.
 */
public class DataFlavor {
    public static final DataFlavor allMimeTypes = new DataFlavor("*/*", null);
    public static final DataFlavor allTextFlavor = new DataFlavor("text/*", null);
    public static final DataFlavor stringFlavor = new DataFlavor("text/plain", null);
    public static final DataFlavor javaJVMLocalFileFlavor = new DataFlavor("JVMLocalFile", null);

    private final Class<?> representationClass;
    private final String mimeType;
    private final String presentation;

    public DataFlavor() {
        this.representationClass = Object.class;
        this.mimeType = "*/*";
        this.presentation = null;
    }

    public DataFlavor(String mimeType) {
        this(mimeType, null);
    }

    public DataFlavor(String mimeType, String presentation) {
        this.representationClass = Object.class;
        this.mimeType = mimeType;
        this.presentation = presentation;
    }

    public DataFlavor(Class<?> representationClass, String presentation) {
        this.representationClass = representationClass;
        this.mimeType = null;
        this.presentation = presentation;
    }

    public Class<?> getRepresentationClass() {
        return representationClass;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getHumanPresentableName() {
        return presentation != null ? presentation : mimeType;
    }

    public boolean isMimeTypeEqual(String mimeType) {
        return this.mimeType != null && this.mimeType.equals(mimeType);
    }

    public boolean isMimeTypeEqual(DataFlavor flavor) {
        return flavor != null && isMimeTypeEqual(flavor.mimeType);
    }

    public boolean isContentTypeEqual(DataFlavor flavor) {
        return flavor != null && isMimeTypeEqual(flavor.mimeType);
    }

    public boolean isRepresentationClassEqual(Class<?> clazz) {
        return representationClass != null && representationClass.equals(clazz);
    }

    public String getName() {
        return mimeType;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DataFlavor)) return false;
        DataFlavor other = (DataFlavor) obj;
        return isMimeTypeEqual(other.mimeType)
                && (representationClass == null || representationClass.equals(other.representationClass));
    }

    @Override
    public int hashCode() {
        return (mimeType != null ? mimeType.hashCode() : 0);
    }
}
