package me.geek.tom.modrinthcli.installcache;

import me.geek.tom.modrinthcli.pack.Pack;
import me.geek.tom.modrinthcli.util.Logger;
import me.geek.tom.modrinthcli.util.VersionPair;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static me.geek.tom.modrinthcli.ModrinthCli.GSON;

public class InstalledModsCacheFile {

    public List<ModCacheEntry> mods;

    public static InstalledModsCacheFile load(Pack pack, boolean delete) throws IOException {
        Path modsDir = pack.getModsDir();
        Path file = modsDir.resolve(".installed-mods");
        if (!Files.exists(file)) {
            Files.write(file, Arrays.asList(
                    "{",
                    "\t\"mods\": []",
                    "}"
            ));
        }
        InstalledModsCacheFile cacheFile = GSON.fromJson(Files.newBufferedReader(file), InstalledModsCacheFile.class);
        cacheFile.cleanup(modsDir, pack.getPackFile().getInstalledMods(), delete);
        return cacheFile;
    }

    public void save(Path modsDir) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(modsDir.resolve(".installed-mods"));
        GSON.toJson(this, writer);
        writer.flush();
        writer.close();
    }

    public void addMod(Path modsDir, String slug, String modId, String versionId, String filename) throws IOException {
        ModCacheEntry entry = ModCacheEntry.of(slug, modId, versionId, filename);
        this.mods.add(entry);
        this.save(modsDir);
    }

    public void cleanup(Path modsDir, List<VersionPair> installedMods, boolean delete) throws IOException {
        List<ModCacheEntry> notInstalledMods = findNotInstalledMods(installedMods);
        if (delete) {
            try {
                notInstalledMods.stream().map(e -> e.getPath(modsDir)).forEach(p -> {
                    try {
                        Logger.info("Deleting: " + p);
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new FailedDeleteException(e);
                    }
                });
            } catch (FailedDeleteException e) {
                throw (IOException) e.getCause();
            }
        }
        this.mods.removeAll(notInstalledMods);
        findMissingMods(modsDir).forEach(mod -> {
            Logger.info("Removing " + mod.modSlug + ":" + mod.versionId + " from index as " + mod.filename + " is missing!");
            this.mods.remove(mod);
        });
        this.save(modsDir);
    }

    public List<ModCacheEntry> findMissingMods(Path dir) {
        List<ModCacheEntry> invalid = new ArrayList<>();
        for (ModCacheEntry mod : mods) {
            if (!Files.exists(dir.resolve(mod.filename))) invalid.add(mod);
        }
        return invalid;
    }

    public List<ModCacheEntry> findNotInstalledMods(List<VersionPair> installedMods) {
        List<ModCacheEntry> invalid = new ArrayList<>();
        for (ModCacheEntry mod : mods) {
            if (!mod.installed(installedMods)) invalid.add(mod);
        }
        return invalid;
    }

    public static class ModCacheEntry {
        public String modSlug;
        public String modId;
        public String versionId;
        public String filename;

        public boolean matches(VersionPair version) {
            return modSlug.equals(version.getModSlug()) && versionId.equals(version.getVersionId());
        }

        public boolean installed(List<VersionPair> installedMods) {
            return installedMods.stream().anyMatch(this::matches);
        }

        public Path getPath(Path modsDir) {
            return modsDir.resolve(this.filename);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModCacheEntry that = (ModCacheEntry) o;
            return modSlug.equals(that.modSlug) && modId.equals(that.modId) && versionId.equals(that.versionId) && filename.equals(that.filename);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modSlug, modId, versionId, filename);
        }

        public static ModCacheEntry of(String slug, String modId, String versionId, String filename) {
            ModCacheEntry entry = new ModCacheEntry();
            entry.modSlug = slug;
            entry.modId = modId;
            entry.versionId = versionId;
            entry.filename = filename;
            return entry;
        }
    }

    private static class FailedDeleteException extends RuntimeException {
        public FailedDeleteException(Throwable cause) {
            super(cause);
        }
    }
}
