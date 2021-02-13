package me.geek.tom.modrinthcli.util;

import me.tongfei.progressbar.*;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static me.geek.tom.modrinthcli.ModrinthCli.OKHTTP;

public class CachedDownloader {
    public static final CachedDownloader INSTANCE = new CachedDownloader(Paths.get(System.getProperty("user.home")).resolve(".modrinth-cli-cache"));

    private final Path cachePath;

    private CachedDownloader(Path cachePath) {
        this.cachePath = cachePath;
        if (!Files.exists(cachePath)) {
            try {
                Files.createDirectories(cachePath);
            } catch (IOException e) {
                System.out.println("Failed to create cache directory!");
            }
        }
    }

    public void download(String url, Path output) throws IOException {
        String filename = output.getFileName().toString();
        Path cacheFile = this.downloadToCache(url, filename);
        Files.copy(cacheFile, output);
    }

    private Path downloadToCache(String url, String filename) throws IOException {
        Path output = this.cachePath.resolve(filename);
        if (Files.exists(output)) return output; // TODO: verify hash?

        Request request = new Request.Builder().url(url).build();
        Response response = OKHTTP.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to download file: " + response);
        }
        ResponseBody body = response.body();
        InputStream is = ProgressBar.wrap(body.byteStream(), new ProgressBarBuilder()
                .setTaskName(getTaskName(filename))
                .setInitialMax(body.contentLength()));
        FileUtils.copyInputStreamToFile(is, output.toFile());
        return output;
    }

    private String getTaskName(String filename) {
        String name = filename.substring(0, filename.lastIndexOf(".") + 1);
        if (name.length() > 7) {
            name = name.substring(0, 7) + "...";
        }
        return name + filename.substring(filename.lastIndexOf(".") + 1);
    }
}
