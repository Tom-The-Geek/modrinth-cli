package me.geek.tom.modrinthcli.util;

public class VersionPair {
    private final String modSlug;
    private final String versionId;

    public VersionPair(String modSlug, String versionId) {
        this.modSlug = modSlug;
        this.versionId = versionId;
    }

    public String getModSlug() {
        return modSlug;
    }

    public String getVersionId() {
        return versionId;
    }
}
