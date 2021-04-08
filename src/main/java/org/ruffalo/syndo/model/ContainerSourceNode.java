package org.ruffalo.syndo.model;

/**
 * Container source nodes consist of a repository image reference usually to an upstream
 * docker repository that must be pulled/present before some other node can be built. Because
 * they are pulled from an upstream they cannot have dependencies themselves.
 *
 * In the build plan these will become the fully-qualified "from image" for FileSourceNodes. The
 * client will do the work of determining if the given image ref is an upstream image that needs
 * to be tagged into an image stream or if it already exists as an image stream.
 *
 * None of the work should be done inside the actual build pod.
 */
public class ContainerSourceNode extends BuildNode {

    private final String imageRef;

    public ContainerSourceNode(final String imageRef) {
        this.imageRef = imageRef;
    }

    @Override
    public BuildNode from() {
        return null;
    }
}
