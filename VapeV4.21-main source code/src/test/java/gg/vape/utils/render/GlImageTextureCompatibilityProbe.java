package gg.vape.utils.render;

public final class GlImageTextureCompatibilityProbe {
    private GlImageTextureCompatibilityProbe() {
    }

    public static void main(String[] arguments) {
        require(GlImageTexture.normalizeTextureParameterValue(10242, 10496) == 33071,
                "GL_TEXTURE_WRAP_S did not normalize GL_CLAMP");
        require(GlImageTexture.normalizeTextureParameterValue(10243, 10496) == 33071,
                "GL_TEXTURE_WRAP_T did not normalize GL_CLAMP");
        require(GlImageTexture.normalizeTextureParameterValue(10241, 10496) == 10496,
                "non-wrap texture parameter was modified");
        require(GlImageTexture.normalizeTextureParameterValue(10242, 10497) == 10497,
                "GL_REPEAT was modified");
        require(GlImageTexture.normalizeTextureParameterValue(10243, 33071) == 33071,
                "GL_CLAMP_TO_EDGE was modified");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
