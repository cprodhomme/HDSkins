package com.minelittlepony.hdskins.client.resources;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;

import com.minelittlepony.hdskins.client.dummy.PlayerSkins;
import com.minelittlepony.hdskins.profile.SkinType;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public class LocalPlayerSkins extends PlayerSkins<LocalPlayerSkins.LocalTexture> {

    private boolean previewThinArms = false;

    public LocalPlayerSkins(Posture posture) {
        super(posture, LocalTexture::new);
    }

    public void setPreviewThinArms(boolean thinArms) {
        previewThinArms = thinArms;
        close();
    }

    @Override
    public boolean usesThinSkin() {
        return previewThinArms;
    }

    public static class LocalTexture implements PlayerSkins.PlayerSkin {
        private final Identifier id;
        private final Supplier<Identifier> defaultTexture;

        private Optional<PreviewTextureManager.FileTexture> local = Optional.empty();

        public LocalTexture(SkinType type, Supplier<Identifier> blank) {
            id = new Identifier("hdskins", "generated_preview/" + type.getPathName());
            defaultTexture = blank;
        }

        @Override
        public Identifier getId() {
            return isReady() ? id : defaultTexture.get();
        }

        public void setLocal(Path file) throws IOException {
            local.ifPresent(AbstractTexture::close);

            try (InputStream input = Files.newInputStream(file)) {
                PreviewTextureManager.FileTexture image = new PreviewTextureManager.FileTexture(HDPlayerSkinTexture.filterPlayerSkins(NativeImage.read(input)), id);
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, image);
                local = Optional.of(image);
            }
        }

        @Override
        public boolean isReady() {
            return local.filter(PreviewTextureManager.Texture::isLoaded).isPresent();
        }

        @Override
        public void close() {
            local.ifPresent(AbstractTexture::close);
            local = Optional.empty();
        }
    }
}
