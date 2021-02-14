package me.geek.tom.modrinthcli.util.cache;

public class DownloadTarget {
    public final String modId;
    public final String versionId;
    public final String sha1;
    public final String url;

    public DownloadTarget(String modId, String versionId, String sha1, String url) {
        this.modId = modId;
        this.versionId = versionId;
        this.sha1 = sha1;
        this.url = url;
    }
}
