package com.minelittlepony.hdskins;

import com.minelittlepony.hdskins.upload.IFileDialog;
import com.minelittlepony.hdskins.upload.ThreadOpenFilePNG;
import com.minelittlepony.hdskins.upload.ThreadSaveFilePNG;
import com.minelittlepony.hdskins.util.MoreHttpResponses;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.NativeImage;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

public class SkinChooser {

    public static final int MAX_SKIN_DIMENSION = 1024;

    public static final String ERR_UNREADABLE = "hdskins.error.unreadable";
    public static final String ERR_EXT = "hdskins.error.ext";
    public static final String ERR_OPEN = "hdskins.error.open";
    public static final String ERR_INVALID = "hdskins.error.invalid";

    public static final String MSG_CHOOSE = "hdskins.choose";

    private static boolean isPowerOfTwo(int number) {
        return number != 0 && (number & number - 1) == 0;
    }

    private IFileDialog openFileThread;

    private final SkinUploader uploader;


    private volatile String status = MSG_CHOOSE;


    public SkinChooser(SkinUploader uploader) {
        this.uploader = uploader;
    }

    public boolean pickingInProgress() {
        return openFileThread != null;
    }

    @Nullable
    public String getStatus() {
        return status;
    }

    public void openBrowsePNG(Minecraft mc, String title) {
        openFileThread = new ThreadOpenFilePNG(mc, title, (file, dialogResult) -> {
            openFileThread = null;
            if (dialogResult == 0) {
                selectFile(file);
            }
        });
        openFileThread.start();
    }

    public void openSavePNG(Minecraft mc, String title) {
        openFileThread = new ThreadSaveFilePNG(mc, title, mc.getSession().getUsername() + ".png", (file, dialogResult) -> {
            if (dialogResult == 0) {
                try (MoreHttpResponses response = uploader.downloadSkin().get()) {
                    if (response.ok()) {
                        Files.copy(response.getInputStream(), file);
                    }
                } catch (IOException | InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            openFileThread = null;
        });
        openFileThread.start();
    }

    public void selectFile(Path skinFile) {
        status = evaluateAndSelect(skinFile);
    }

    @Nullable
    private String evaluateAndSelect(Path skinFile) {
        if (!Files.exists(skinFile)) {
            return ERR_UNREADABLE;
        }

        if (!FilenameUtils.isExtension(skinFile.getFileName().toString(), new String[]{"png", "PNG"})) {
            return ERR_EXT;
        }

        try (InputStream in = Files.newInputStream(skinFile)) {
            NativeImage chosenImage = NativeImage.read(in);

            if (!acceptsSkinDimensions(chosenImage.getWidth(), chosenImage.getHeight())) {
                return ERR_INVALID;
            }

            uploader.setLocalSkin(skinFile);

            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ERR_OPEN;
    }

    protected boolean acceptsSkinDimensions(int w, int h) {
        return isPowerOfTwo(w) && w == h * 2 || w == h && w <= MAX_SKIN_DIMENSION && h <= MAX_SKIN_DIMENSION;
    }
}
