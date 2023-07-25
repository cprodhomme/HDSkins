package com.minelittlepony.hdskins.client.gui;

import com.minelittlepony.common.client.gui.ITextContext;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;

public class StatusBanner extends DrawableHelper implements ITextContext {
    public static final Text HD_SKINS_UPLOAD = Text.translatable("hdskins.upload");
    public static final Text HD_SKINS_REQUEST = Text.translatable("hdskins.request");
    public static final Text HD_SKINS_FAILED = Text.translatable("hdskins.failed");

    private final SkinUploader uploader;

    private boolean showing;
    private float msgFadeOpacity = 0;
    private Text lastShownMessage = Text.empty();

    public StatusBanner(SkinUploader uploader) {
        this.uploader = uploader;
    }

    public void render(MatrixStack matrices, float deltaTime, int width, int height) {

        boolean showBanner = uploader.hasBannerMessage();

        if (showBanner != showing) {
            showing = showBanner;
            if (showBanner) {
                lastShownMessage = uploader.getBannerMessage();
            }
        } else {
            if (showBanner) {
                Text updatedMessage = uploader.getBannerMessage();
                if (updatedMessage != lastShownMessage) {
                    lastShownMessage = updatedMessage;
                }
            }
        }

        if (showing) {
            msgFadeOpacity += deltaTime / 6;
        } else {
            msgFadeOpacity -= deltaTime / 6;
        }

        msgFadeOpacity = MathHelper.clamp(msgFadeOpacity, 0, 1);

        if (msgFadeOpacity > 0) {
            matrices.push();
            int opacity = (Math.min(180, (int)(msgFadeOpacity * 180)) & 255) << 24;

            fill(matrices, 0, 0, width, height, opacity);

            if (showBanner || msgFadeOpacity >= 1) {
                int maxWidth = Math.min(width - 10, getFont().getWidth(lastShownMessage));
                int messageHeight = getFont().getWrappedLinesHeight(lastShownMessage.getString(), maxWidth) + getFont().fontHeight + 10;
                int blockY = (height - messageHeight) / 2;
                int blockX = (width - maxWidth) / 2;
                int padding = 6;

                drawTooltipDecorations(matrices, blockX - padding, blockY - padding, maxWidth + padding * 2, messageHeight + padding * 2);
                matrices.translate(0, 0, 400);

                if (lastShownMessage != HD_SKINS_UPLOAD && lastShownMessage != HD_SKINS_REQUEST) {
                    drawCenteredLabel(matrices, HD_SKINS_FAILED, width / 2, blockY, 0xffff55, 0);
                    drawTextBlock(matrices, lastShownMessage, blockX, blockY + getFont().fontHeight + 10, maxWidth, 0xff5555);
                } else {
                    uploader.tryClearStatus();
                    drawCenteredLabel(matrices, lastShownMessage, width / 2, height / 2, 0xffffff, 0);
                }
            }

            matrices.pop();
        }
    }

    public boolean isVisible() {
        return msgFadeOpacity > 0;
    }

    static void drawTooltipDecorations(MatrixStack matrices, int x, int y, int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        int k = width;
        int l = x;
        int m = y;
        int n = height;
        fillGradient(matrix4f, buffer, l - 3, m - 4, l + k + 3, m - 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, buffer, l - 3, m + n + 3, l + k + 3, m + n + 4, 400, -267386864, -267386864);
        fillGradient(matrix4f, buffer, l - 3, m - 3, l + k + 3, m + n + 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, buffer, l - 4, m - 3, l - 3, m + n + 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, buffer, l + k + 3, m - 3, l + k + 4, m + n + 3, 400, -267386864, -267386864);
        fillGradient(matrix4f, buffer, l - 3, m - 3 + 1, l - 3 + 1, m + n + 3 - 1, 400, 0x505000FF, 1344798847);
        fillGradient(matrix4f, buffer, l + k + 2, m - 3 + 1, l + k + 3, m + n + 3 - 1, 400, 0x505000FF, 1344798847);
        fillGradient(matrix4f, buffer, l - 3, m - 3, l + k + 3, m - 3 + 1, 400, 0x505000FF, 0x505000FF);
        fillGradient(matrix4f, buffer, l - 3, m + n + 2, l + k + 3, m + n + 3, 400, 1344798847, 1344798847);

        BufferRenderer.drawWithShader(buffer.end());
    }
}
