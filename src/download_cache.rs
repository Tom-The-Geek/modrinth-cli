use std::path::{Path, PathBuf};

use crate::modrinth::{ModrinthModVersionFile, ModrinthModVersion};
use reqwest::Client;
use std::fmt::{Display, Formatter};

pub struct DownloadCache {
    root: Box<Path>,
    client: Client,
}

pub struct DownloadTarget {
    pub mod_id: String,
    pub version_id: String,
    pub sha1: String,
    pub url: String,
}

impl Display for DownloadTarget {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}:{}", self.mod_id, self.version_id)
    }
}

impl DownloadTarget {
    pub fn from(mod_version: &ModrinthModVersion, mod_file: &ModrinthModVersionFile) -> Self {
        return Self {
            mod_id: mod_version.mod_id.clone(),
            version_id: mod_version.id.clone(),
            url: mod_file.url.clone(),
            sha1: mod_file.hashes.get("sha1").unwrap().clone()
        }
    }
}

impl DownloadCache {
    pub async fn copy_from_cache(&self, download: &DownloadTarget, output: &Path) -> Result<(), Box<dyn std::error::Error>> {
        let cache_path = self.get_cache_path(download);
        if !cache_path.exists() {
            match crate::download::download(&self.client, &*download.url, &cache_path).await {
                Err(e) => return Err(e),
                _ => { }
            };
        } else {
            println!("Local cache hit for {}", download)
        }

        std::fs::copy(cache_path, output)?;
        Ok(())
    }

    fn get_cache_path(&self, download: &DownloadTarget) -> PathBuf {
        return self.root.join(&download.mod_id).join(&download.version_id).join(format!("{}{}", &download.sha1, ".cache"));
    }

    pub fn create(root: &Path) -> std::io::Result<Self> {
        if !root.exists() {
            std::fs::create_dir_all(&root)?
        }

        Ok(Self {
            root: Box::from(root),
            client: Client::new()
        })
    }
}
