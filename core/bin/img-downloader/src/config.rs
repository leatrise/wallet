use config::{Config, ConfigError, Environment, File};
use serde::Deserialize;
use serde_serializers::duration;
use std::time::Duration;
use std::{env, path::PathBuf};

#[derive(Debug, Deserialize, Clone)]
pub struct ImgDownloaderConfig {
    pub folder: String,
    pub image_size: u32,
    #[serde(deserialize_with = "duration::deserialize")]
    pub delay: Duration,
    pub coingecko: CoingeckoConfig,
    pub jupiter: JupiterConfig,
}

#[derive(Debug, Deserialize, Clone)]
pub struct CoingeckoConfig {
    pub top_count: usize,
}

#[derive(Debug, Deserialize, Clone)]
pub struct JupiterConfig {
    pub minimum_market_cap: f64,
}

impl ImgDownloaderConfig {
    pub fn load() -> Result<Self, ConfigError> {
        let current_dir = env::current_dir().map_err(|error| ConfigError::Message(error.to_string()))?;
        let base_dir = if current_dir.join("config.yml").exists() {
            current_dir
        } else {
            current_dir.join("bin/img-downloader")
        };
        Self::load_from_path(base_dir.join("config.yml"))
    }

    fn load_from_path(path: PathBuf) -> Result<Self, ConfigError> {
        Config::builder()
            .add_source(File::from(path))
            .add_source(Environment::default().separator("_"))
            .build()?
            .try_deserialize()
    }
}
