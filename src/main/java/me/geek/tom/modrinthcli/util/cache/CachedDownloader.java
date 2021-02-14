package me.geek.tom.modrinthcli.util.cache;

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

    public void download(DownloadTarget target, Path output) throws IOException {
        String filename = output.getFileName().toString();
        Path cacheFile = this.getFromCache(target, filename);
        Files.copy(cacheFile, output);
    }

    private boolean isInCache(DownloadTarget target) {
        return Files.exists(this.getCachePath(target));
    }

    private Path getCachePath(DownloadTarget target) {
        return this.cachePath.resolve(target.modId).resolve(target.versionId).resolve(target.sha1 + ".cache");
    }

    private Path downloadToCacheFile(DownloadTarget target, String filename) throws IOException {
        Path output = this.getCachePath(target);

        Request request = new Request.Builder().url(target.url).build();
        Response response = OKHTTP.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to download file: " + response);
        }
        ResponseBody body = response.body();

        assert body != null;
        InputStream is = ProgressBar.wrap(body.byteStream(), new ProgressBarBuilder()
                .setTaskName(getTaskName(filename))
                .setInitialMax(body.contentLength()));
        FileUtils.copyInputStreamToFile(is, output.toFile());

        return output;
    }

    private Path getFromCache(DownloadTarget target, String filename) throws IOException {
        if (this.isInCache(target)) {
            return this.getCachePath(target);
        }

        return downloadToCacheFile(target, filename);
    }

    private String getTaskName(String filename) {
        String name = filename.substring(0, filename.lastIndexOf(".") + 1);
        if (name.length() > 7) {
            name = name.substring(0, 7) + "...";
        }
        return name + filename.substring(filename.lastIndexOf(".") + 1);
    }
}
