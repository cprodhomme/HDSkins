package com.voxelmodpack.hdskins;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.common.cache.Cache;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.InsecureTextureException;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftProfileTexture.Type;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mumfrey.liteloader.core.LiteLoader;
import com.mumfrey.liteloader.transformers.event.EventInfo;
import com.mumfrey.liteloader.util.log.LiteLoaderLogger;
import com.voxelmodpack.common.runtime.PrivateFields;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StringUtils;

public final class HDSkinManager {
    private static String gatewayUrl = "skinmanager.voxelmodpack.com";
    private static String skinUrl = "skins.voxelmodpack.com";
    private static Cache<GameProfile, Map<Type, MinecraftProfileTexture>> skinsCache;
    private static final BiMap<String, String> playerHashes = HashBiMap.create();
    private static final Map<String, Map<Type, MinecraftProfileTexture>> cachedTextures = new HashMap<String, Map<Type, MinecraftProfileTexture>>();

    public static void onDownloadSkin(EventInfo<ThreadDownloadImageData> e) {
        ThreadDownloadImageData imageDownload = e.getSource();
        if (imageDownload != null) {
            String imageUrl = PrivateFields.imageUrl.get(imageDownload);
            if (imageUrl != null) {
                String hash = FilenameUtils.getBaseName(imageUrl);
                String uuid = resolvePlayerIdFromHash(hash);
                if (uuid == null) {
                    if (!(imageDownload instanceof PreviewTexture)) {
                        return;
                    }

                    uuid = Minecraft.getMinecraft().getSession().getPlayerID();
                }

                Map<Type, MinecraftProfileTexture> textures = getCachedTexturesForId(uuid);
                MinecraftProfileTexture skinTexture = textures.get(Type.SKIN);
                if (skinTexture != null && skinTexture.getUrl().equals(imageUrl)) {
                    Thread imageThread = PrivateFields.imageThread.get(imageDownload);
                    if (imageThread != null) {
                        HDSkinDownload hdThread = new HDSkinDownload(imageDownload, new ImageBufferDownloadHD(),
                                getCustomSkinURLForId(uuid, imageDownload instanceof PreviewTexture));
                        PrivateFields.imageThread.set(imageDownload, hdThread);
                        hdThread.setDaemon(true);
                        hdThread.start();
                        e.cancel();
                    }
                } else {
                    LiteLoaderLogger.debug("Not a skin texture!");
                }
            }
        }
    }

    private static String resolvePlayerIdFromHash(String hash) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return null;
        }
        Collection<NetworkPlayerInfo> playersInfo = mc.getNetHandler().func_175106_d();
        String uuid;
        // players
        for (NetworkPlayerInfo player : playersInfo) {
            GameProfile profile = player.getGameProfile();
            Map<Type, MinecraftProfileTexture> textures = getTexturesForProfile(mc, profile);
            storeTexturesForProfile(profile, textures);
            uuid = findUUID(profile, textures, hash);
            if (uuid != null)
                return uuid;
        }
        @SuppressWarnings("unchecked")
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        for (EntityPlayer player : players) {
            GameProfile profile = player.getGameProfile();
            Map<Type, MinecraftProfileTexture> textures = getTexturesForProfile(mc, profile);
            storeTexturesForProfile(profile, textures);
            uuid = findUUID(profile, textures, hash);
            if (uuid != null)
                return uuid;
        }
        // skulls
        for (Entry<GameProfile, Map<Type, MinecraftProfileTexture>> e : getSkinsCache().asMap().entrySet()) {
            GameProfile profile = e.getKey();
            Map<Type, MinecraftProfileTexture> textures = e.getValue();
            storeTexturesForProfile(profile, textures);
            uuid = findUUID(profile, textures, hash);
            if (uuid != null)
                return uuid;
        }

        return null;
    }

    private static String findUUID(GameProfile profile, Map<?, MinecraftProfileTexture> textures, String hash) {
        for (MinecraftProfileTexture texture : textures.values()) {
            if (hash.equals(texture.getHash())) {
                String uuid = trimUUID(profile.getId());
                playerHashes.put(hash, uuid);
                return uuid;
            }
        }
        return null;
    }

    private static void storeTexturesForProfile(GameProfile profile, Map<Type, MinecraftProfileTexture> textures) {
        Map<?, ?> cached = getCachedTexturesForId(trimUUID(profile.getId()));
        if (cached == null && textures != null && !textures.isEmpty()) {
            LiteLoaderLogger.debug("Store textures for " + profile.getId());
            cachedTextures.put(trimUUID(profile.getId()), textures);
        }
    }

    private static Map<Type, MinecraftProfileTexture> getTexturesForProfile(Minecraft minecraft, GameProfile profile) {
        LiteLoaderLogger.debug("Get textures for " + profile.getId(), new Object[0]);
        Map<Type, MinecraftProfileTexture> cached = getCachedTexturesForId(trimUUID(profile.getId()));
        if (cached != null) {
            return cached;
        } else {
            MinecraftSessionService sessionService = minecraft.getSessionService();
            Map<Type, MinecraftProfileTexture> textures = null;

            try {
                textures = sessionService.getTextures(profile, true);
            } catch (InsecureTextureException var6) {
                textures = sessionService.getTextures(profile, false);
            }

            if ((textures == null || textures.isEmpty())
                    && profile.getId().equals(minecraft.getSession().getProfile().getId())) {
                textures = sessionService.getTextures(sessionService.fillProfileProperties(profile, false), false);
            }

            storeTexturesForProfile(profile, textures);

            return textures;
        }
    }

    private static Cache<GameProfile, Map<Type, MinecraftProfileTexture>> getSkinsCache() {
        if (skinsCache == null) {
            // final field isn't going to change
            skinsCache = HDPrivateFields.skinCacheLoader.get(Minecraft.getMinecraft().getSkinManager());
        }
        return skinsCache;
    }

    private static Map<Type, MinecraftProfileTexture> getCachedTexturesForId(String uuid) {
        return cachedTextures.get(uuid);
    }

    private static String trimUUID(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    public static void setSkinUrl(String skinUrl) {
        HDSkinManager.skinUrl = skinUrl;
    }

    public static void setGatewayURL(String gatewayURL) {
        gatewayUrl = gatewayURL;
    }

    public static String getSkinUrl() {
        return String.format("http://%s/", skinUrl);
    }

    public static String getGatewayUrl() {
        return String.format("http://%s/", gatewayUrl);
    }

    public static String getCustomSkinURLForId(String uuid, boolean gateway) {
        uuid = StringUtils.stripControlCodes(uuid);
        return String.format("http://%s/skins/%s.png", gateway ? gatewayUrl : skinUrl, uuid);
    }

    public static String getCustomCloakURLForId(String uuid) {
        return String.format("http://%s/capes/%s.png", skinUrl, StringUtils.stripControlCodes(uuid));
    }

    public static PreviewTexture getPreviewTexture(ResourceLocation skinResource, GameProfile profile) {
        TextureManager textureManager = Minecraft.getMinecraft().getTextureManager();
        Object skinTexture = textureManager.getTexture(skinResource);
        if (skinTexture == null) {
            Map<Type, MinecraftProfileTexture> textures = getTexturesForProfile(Minecraft.getMinecraft(), profile);
            MinecraftProfileTexture skin = textures.get(Type.SKIN);
            if (skin == null) {
                throw new RuntimeException("Could not get player skin URL from profile");
            }

            String url = skin.getUrl();
            skinTexture = new PreviewTexture(url, DefaultPlayerSkin.getDefaultSkin(profile.getId()),
                    new ImageBufferDownloadHD());
            textureManager.loadTexture(skinResource, (ITextureObject) skinTexture);
        }

        return (PreviewTexture) skinTexture;
    }

    public static void clearSkinCache() {
        LiteLoaderLogger.info("Clearing local player skin cache", new Object[0]);

        try {
            FileUtils.deleteDirectory(new File(LiteLoader.getAssetsDirectory(), "skins"));
        } catch (IOException var1) {
            var1.printStackTrace();
        }
        // clear the maps, too
        getSkinsCache().invalidateAll();
        cachedTextures.clear();
        playerHashes.clear();

    }
}
