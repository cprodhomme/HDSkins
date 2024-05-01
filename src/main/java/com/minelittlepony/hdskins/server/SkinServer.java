package com.minelittlepony.hdskins.server;

import com.minelittlepony.hdskins.profile.SkinType;
import com.minelittlepony.hdskins.server.SkinUpload.Session;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import java.io.IOException;
import java.util.*;

public interface SkinServer {
    /**
     * Returns the set of features that this skin server supports.
     */
    Set<Feature> getFeatures();

    /**
     * Determines whether this server is the source of the provided url.
     */
    boolean ownsUrl(String url);

    /**
     * Returns whether this skin server supports a particular skin type.
     * It's recommended to implement this on an exclusion bases:
     *  return false for the things you <i>don't</i> support.
     */
    boolean supportsSkinType(SkinType skinType);

    /**
     * Checks whether the current session is valid.
     * @param session The current session
     *
     * @throws IOException
     * @throws AuthenticationException
     * @see MinecraftSessionService.joinServer
     */
    void authorize(Session session) throws IOException, AuthenticationException;

    /**
     * Loads texture information for the current user.
     *
     * @param session The current user's session
     * @return The current user's texture info
     * @throws IOException             If any network errors occur
     * @throws AuthenticationException If there are issues with authentication
     */
    default TexturePayload loadSkins(Session session) throws IOException, AuthenticationException {
        return loadSkins(session.profile());
    }

    /**
     * Loads texture information for the provided profile.
     *
     * @return The parsed server response as a textures payload.
     * @throws IOException If any authentication or network error occurs.
     */
    TexturePayload loadSkins(GameProfile profile) throws IOException, AuthenticationException;

    /**
     * Uploads a player's skin to this server.
     *
     * @param upload The payload to send.
     * @return A server response object.
     *
     * @throws IOException
     * @throws AuthenticationException
     */
    void uploadSkin(SkinUpload upload) throws IOException, AuthenticationException;

    /***
     * Loads a player's detailed profile from this server.
     * @param profile The game profile of the player being queried
     * @return The pre-populated profile of the given player.
     * @throws IOException
     * @throws AuthenticationException
     */
    default Optional<SkinServerProfile<?>> loadProfile(GameProfile profile) throws IOException, AuthenticationException {
        return Optional.empty();
    }

    interface SkinServerProfile<T extends SkinServerProfile.Skin> {
        GameProfile getGameProfile();

        List<T> getSkins(SkinType type);

        void setActive(SkinType type, T texture);

        interface Skin {
            String getModel();

            boolean isActive();

            String getUri();
        }
    }
}
