use std::path::Path;
use std::{fs, io};

use serde::{Deserialize, Serialize};
use crate::modrinth::PackMod;

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallCache {
    pub mods: Vec<InstalledMod>
}

impl InstallCache {
    pub fn load(mods_dir: &Path) -> io::Result<Self> {
        let path = mods_dir.join(".installed-mods");
        if !path.exists() {
            Self::empty().save(mods_dir)?;
        }
        let json_data = fs::read_to_string(path)?;
        Ok(serde_json::from_str(&*json_data)?)
    }

    pub fn add_mod(&mut self, mods_dir: &Path, pack_mod: &PackMod, filename: &String) -> io::Result<()> {
        self.mods.push(InstalledMod::of(
            pack_mod.slug.clone(),
            pack_mod.id.clone(),
            pack_mod.version.clone(),
            filename.clone()
        ));

        self.save(mods_dir)
    }

    pub fn save(&self, mods_dir: &Path) -> io::Result<()> {
        fs::write(mods_dir.join(".installed-mods"), serde_json::to_string(self)?)
    }

    pub fn cleanup(&mut self, mods_dir: &Path, installed_mods: Vec<(String, String)>, delete: bool) -> std::io::Result<()> {
        let not_installed_mods = (&self).find_not_installed_mods(&installed_mods);

        for not_installed_mod in &not_installed_mods {
            if delete {
                let path = mods_dir.join(not_installed_mod.filename.clone());
                println!("Deleting {} as it is no-longer installed!", not_installed_mod.mod_slug);
                std::fs::remove_file(path)?;
            }
        }

        let missing_mods = self.find_missing_mods(mods_dir);
        // self.mods = self.mods.into_iter().filter(|v| {
        //     !(&not_installed_mods).contains(&v) && !missing_mods.contains(&v)
        // }).collect();

        let mut idxs: Vec<usize> = vec![];
        let mut i = 0;
        for m in &self.mods {
            if not_installed_mods.contains(&&m) || missing_mods.contains(&&m) {
                idxs.insert(0, i);
            }
            i = i + 1;
        }

        for i in idxs {
            self.mods.remove(i);
        }

        self.save(mods_dir)?;

        Ok(())
    }

    fn find_not_installed_mods(&self, installed_mods: &Vec<(String, String)>) -> Vec<&InstalledMod> {
        let mut not_installed: Vec<&InstalledMod> = vec![];

        for installed_mod in &self.mods {
            let mut installed = false;
            for m in installed_mods {
                if m.0 == installed_mod.mod_slug && m.1 == installed_mod.version_id {
                    installed = true;
                }
            }
            if !installed {
                not_installed.push(&installed_mod);
            }
        }

        return not_installed;
    }

    fn find_missing_mods(&self, mods_dir: &Path) -> Vec<&InstalledMod> {
        let mut missing: Vec<&InstalledMod> = vec![];

        for installed_mod in &self.mods {
            if !mods_dir.join(&installed_mod.filename).exists() {
                missing.push(&installed_mod);
            }
        }

        return missing;
    }

    fn empty() -> Self {
        Self {
            mods: vec![],
        }
    }
}

#[derive(Deserialize, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct InstalledMod {
    pub mod_slug: String,
    pub mod_id: String,
    pub version_id: String,
    pub filename: String,
}

impl InstalledMod {
    fn of(slug: String, mod_id: String, version_id: String, filename: String) -> Self {
        Self {
            mod_slug: slug,
            mod_id,
            version_id,
            filename,
        }
    }
}
