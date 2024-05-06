package com.minelittlepony.hdskins.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.*;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.minelittlepony.hdskins.HDSkinsServer;
import com.minelittlepony.hdskins.profile.ProfileUtils;
import com.minelittlepony.hdskins.profile.SkinType;
import com.minelittlepony.hdskins.server.SkinUpload.Session;
import com.minelittlepony.hdskins.util.IndentedToStringStyle;
import com.minelittlepony.hdskins.util.net.FileTypes;
import com.minelittlepony.hdskins.util.net.MoreHttpResponses;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.InsecurePublicKeyException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;

import net.minecraft.util.Util;

@ServerType("mojang")
public class YggdrasilSkinServer implements SkinServer {

    static final SkinServer INSTANCE = new YggdrasilSkinServer();

    private static final Set<Feature> FEATURES = Sets.newHashSet(
            Feature.SYNTHETIC,
            Feature.UPLOAD_USER_SKIN,
            Feature.DOWNLOAD_USER_SKIN,
            Feature.DELETE_USER_SKIN,
            Feature.MODEL_VARIANTS,
            Feature.MODEL_TYPES);

    private transient final String address = "https://api.minecraftservices.com";
    private transient final String verify = "https://authserver.mojang.com/validate";

    private transient final String skinUploadAddress = address + "/minecraft/profile/skins";
    private transient final String activeSkinAddress = skinUploadAddress + "/active";
    private transient final String activeCapeAddress = address + "/minecraft/profile/capes/active";

    private transient final boolean requireSecure = true;

    @Override
    public boolean ownsUrl(String url) {
        return false;
    }

    @Override
    public Set<Feature> getFeatures() {
        return FEATURES;
    }

    @Override
    public boolean supportsSkinType(SkinType skinType) {
        return skinType.isVanilla() && skinType != SkinType.CAPE;
    }

    @Override
    public TexturePayload loadSkins(GameProfile profile) throws IOException, AuthenticationException {
        MinecraftSessionService service = HDSkinsServer.getInstance().getSessionService();

        ProfileResult result = service.fetchProfile(profile.getId(), requireSecure);

        if (result == null) {
            throw new AuthenticationException("Mojang API error occured. You may be throttled.");
        }

        try {
            profile = result.profile();
            return new TexturePayload(profile, ProfileUtils.readVanillaTexturesBlob(service, profile).findFirst().orElseGet(HashMap::new));
        } catch (InsecurePublicKeyException e) {
            throw new AuthenticationException(e);
        }
    }

    @Override
    public void uploadSkin(SkinUpload upload) throws IOException, AuthenticationException {
        authorize(upload.session());

        if (upload instanceof SkinUpload.Delete) {
            execute(HttpRequest.newBuilder(URI.create(activeSkinAddress))
                    .DELETE()
                    .header(FileTypes.HEADER_AUTHORIZATION, "Bearer " + upload.session().accessToken())
                    .build());
        } else if (upload instanceof SkinUpload.FileUpload fileUpload) {
            execute(HttpRequest.newBuilder(URI.create(skinUploadAddress))
                    .PUT(FileTypes.multiPart(mapMetadata(fileUpload.metadata()))
                            .field("file", fileUpload.file())
                            .build())
                    .header(FileTypes.HEADER_CONTENT_TYPE, FileTypes.MULTI_PART_FORM_DATA)
                    .header(FileTypes.HEADER_ACCEPT, FileTypes.APPLICATION_JSON)
                    .header(FileTypes.HEADER_AUTHORIZATION, "Bearer " + upload.session().accessToken())
                    .build());
        } else if (upload instanceof SkinUpload.UriUpload uriUpload) {
            // https://wiki.vg/Mojang_API#Change_Skin
            execute(HttpRequest.newBuilder(URI.create(skinUploadAddress))
                    .POST(FileTypes.json(mapMetadata(Util.make(uriUpload.metadata(), metadata -> {
                        metadata.put("url", uriUpload.uri().toString());
                    }))))
                    .header(FileTypes.HEADER_CONTENT_TYPE, FileTypes.MULTI_PART_FORM_DATA)
                    .header(FileTypes.HEADER_ACCEPT, FileTypes.APPLICATION_JSON)
                    .header(FileTypes.HEADER_AUTHORIZATION, "Bearer " + upload.session().accessToken())
                    .build());
        } else {
            throw new IllegalArgumentException("Unsupported SkinUpload type: " + upload.getClass());
        }

        // TODO:
        // MinecraftClient client = MinecraftClient.getInstance();
        // client.getSessionProperties().clear();
    }

    private Map<String, String> mapMetadata(Map<String, String> metadata) {
        Map<String, String> result = new HashMap<>();
        String model = metadata.getOrDefault("model", "classic");
        result.put("variant", "default".contentEquals(model) ? "classic" : model);
        return result;
    }

    @Override
    public void authorize(Session session) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("accessToken", session.accessToken());
        execute(HttpRequest.newBuilder(URI.create(verify))
                .POST(BodyPublishers.ofString(json.toString()))
                .header(FileTypes.HEADER_CONTENT_TYPE, FileTypes.APPLICATION_JSON)
                .header(FileTypes.HEADER_ACCEPT, FileTypes.APPLICATION_JSON)
                .build());
    }

    private void execute(HttpRequest request) throws IOException {
        MoreHttpResponses response = MoreHttpResponses.execute(request);
        if (!response.ok()) {
            throw new IOException(response.json(ErrorResponse.class, "Server did not respond correctly. Status Code " + response.response().statusCode()).toString());
        }
    }

    public void setActiveCape(Session session, String capeId) throws IOException, AuthenticationException {
        authorize(session);
        execute(HttpRequest.newBuilder(URI.create(activeCapeAddress))
                .PUT(FileTypes.json(Map.of("capeId", capeId)))
                .build());
    }

    @Override
    public Optional<SkinServerProfile<?>> loadProfile(Session session) throws IOException, AuthenticationException {
        MoreHttpResponses response = MoreHttpResponses.execute(HttpRequest.newBuilder(URI.create(activeSkinAddress))
                .GET()
                .header(FileTypes.HEADER_AUTHORIZATION, "Bearer " + session.accessToken())
                .build());

        if (!response.ok()) {
            return Optional.empty();
        }

        ProfileResponse prof = response.json(ProfileResponse.class, "Server send invalid profile response");
        prof.session = session;
        prof.server = this;
        return Optional.of(prof);
    }

    @Override
    public String toString() {
        return new IndentedToStringStyle.Builder(this)
                .append("address", address)
                .append("secured", requireSecure)
                .toString();
    }

    class ErrorResponse {
        String error;
        String errorMessage;

        @Override
        public String toString() {
            return String.format("%s: %s", error, errorMessage);
        }
    }

    static class ProfileResponse implements SkinServerProfile<ProfileResponse.Skin> {
        public String id;
        public String name;
        public List<Skin> skins;
        public List<Skin> capes;

        transient Session session;
        transient YggdrasilSkinServer server;

        static class Skin implements SkinServerProfile.Skin {
            public String id;
            public State state;
            public String url;
            public String textureKey;
            public String variant;

            @Override
            public String getModel() {
                return variant;
            }

            @Override
            public boolean isActive() {
                return state == State.ACTIVE;
            }

            @Override
            public String getUri() {
                return url;
            }
        }

        enum State {
            ACTIVE,
            INACTIVE
        }

        @Override
        public List<Skin> getSkins(SkinType type) {
            if (type == SkinType.SKIN) {
                return skins;
            }
            if (type == SkinType.CAPE) {
                return capes;
            }
            return List.of();
        }

        @Override
        public void setActive(SkinType type, Skin texture) throws IOException, AuthenticationException {
            if (texture.state == State.ACTIVE) {
                return;
            }
            getSkins(type).forEach(s -> s.state = State.INACTIVE);
            texture.state = State.ACTIVE;
            if (type == SkinType.CAPE) {
                server.setActiveCape(session, texture.id);
            }
        }
    }
}
