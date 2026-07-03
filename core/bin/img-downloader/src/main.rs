mod cli_args;
mod config;
mod downloader;
mod image;
mod providers;

use clap::Parser;
use config::ImgDownloaderConfig;
use downloader::Downloader;
use settings::Settings;
use std::error::Error;

#[tokio::main]
async fn main() -> Result<(), Box<dyn Error + Send + Sync>> {
    let args = cli_args::Args::parse();
    let config = ImgDownloaderConfig::load()?;
    let settings = Settings::new()?;
    let downloader = Downloader::new(args, settings.coingecko.key.secret, config);

    downloader.start().await
}
