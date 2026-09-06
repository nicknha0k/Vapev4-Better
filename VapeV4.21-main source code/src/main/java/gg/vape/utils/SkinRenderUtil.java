package gg.vape.utils;

import gg.vape.account.MinecraftSessionWrapper;
import gg.vape.wrapper.impl.EntityPlayer;
import gg.vape.wrapper.impl.EntityPlayerSP;
import gg.vape.wrapper.impl.Minecraft;
import gg.vape.wrapper.impl.PlayerInfo;
import gg.vape.wrapper.impl.ResourceLocation;
import java.util.Collection;
import java.util.UUID;
import org.lwjgl.opengl.GL11;

public final class SkinRenderUtil {
    private SkinRenderUtil() {
    }

    public static String getLocalUsername() {
        try {
            MinecraftSessionWrapper session = Minecraft.Q$src$Lgg_vape_account_MinecraftSessionWrapper_$1ftnn3u();
            if (session == null) {
                return null;
            }
            return session.getUsername();
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    public static ResourceLocation getLocalSkinLocation() {
        try {
            EntityPlayerSP player = Minecraft.thePlayer();
            if (player == null || player.isNull()) {
                return null;
            }
            UUID uuid = new EntityPlayer(player.getObject()).c$src$Lgg_vape_wrapper_impl_GameProfile_$ir8937().getUUID();
            Collection<?> infos = player.sendQueue().getPlayerInfoMap();
            if (infos == null) {
                return null;
            }
            for (Object info : infos) {
                try {
                    PlayerInfo playerInfo = new PlayerInfo(info);
                    if (uuid.equals(playerInfo.v().getUUID())) {
                        ResourceLocation skin = playerInfo.i();
                        if (skin != null && !skin.isNull()) {
                            return skin;
                        }
                        return null;
                    }
                }
                catch (Throwable ignored) {
                }
            }
        }
        catch (Throwable ignored) {
        }
        return null;
    }

    public static void drawPlayerFace(ResourceLocation skin, double x, double y, double size) {
        if (skin == null || skin.isNull()) {
            return;
        }
        try {
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            Minecraft.getTextureManager().bindTexture(skin);
            drawTexturedQuad(x, y, size, 8.0, 8.0, 8.0, 8.0);
            drawTexturedQuad(x, y, size, 40.0, 8.0, 8.0, 8.0);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
        catch (Throwable ignored) {
            try {
                GL11.glPopAttrib();
            }
            catch (Throwable ignored2) {
            }
            try {
                GL11.glPopMatrix();
            }
            catch (Throwable ignored3) {
            }
        }
    }

    private static void drawTexturedQuad(double x, double y, double size, double u, double v, double regionWidth, double regionHeight) {
        double textureSize = 64.0;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(u / textureSize, v / textureSize);
        GL11.glVertex2d(x, y);
        GL11.glTexCoord2d(u / textureSize, (v + regionHeight) / textureSize);
        GL11.glVertex2d(x, y + size);
        GL11.glTexCoord2d((u + regionWidth) / textureSize, (v + regionHeight) / textureSize);
        GL11.glVertex2d(x + size, y + size);
        GL11.glTexCoord2d((u + regionWidth) / textureSize, v / textureSize);
        GL11.glVertex2d(x + size, y);
        GL11.glEnd();
    }
}
