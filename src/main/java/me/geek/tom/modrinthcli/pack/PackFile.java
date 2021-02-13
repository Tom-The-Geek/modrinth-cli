package me.geek.tom.modrinthcli.pack;

import com.electronwill.nightconfig.core.Config;
import me.geek.tom.modrinthcli.util.VersionPair;

import java.util.List;
import java.util.stream.Collectors;

public class PackFile {
    public Details details = new Details();
    public Config mods;

    public List<VersionPair> getInstalledMods() {
        return mods.valueMap().entrySet().stream().map(e -> new VersionPair(e.getKey(), (String) e.getValue())).collect(Collectors.toList());
    }

    public static class Details {
        public String loader = "";
        public String gameVersion = "";
    }
}
