package io.github.chrisruffalo.syndo.model;

/**
 * Container source nodes consist of a repository image reference to an upstream
 * docker repository that must be pulled/present before some other node can be built. Because
 * they are pulled from an upstream they cannot have dependencies themselves.
 *
 * In the build plan these will become the fully-qualified "from image" for FileSourceNodes. The
 * client will do the work of determining if the given image ref is an upstream image that needs
 * to be tagged into an image stream or if it already exists as an image stream.
 *
 * None of the work should be done inside the actual build pod.
 */
public class ImageRefSourceNode extends BuildNode {

    private final String imageRef;
    private boolean resolvedInternally;

    public ImageRefSourceNode(final String imageRef) {
        this.imageRef = imageRef;
    }

    public String getImageRef() {
        return imageRef;
    }

    public boolean isResolvedInternally() {
        return resolvedInternally;
    }

    public void setResolvedInternally(boolean resolvedInternally) {
        this.resolvedInternally = resolvedInternally;
    }
}
