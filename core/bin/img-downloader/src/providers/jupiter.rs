use super::model::AssetImage;
use primitives::Chain;
use serde::Deserialize;
use std::error::Error;

const JUPITER_API_BASE_URL: &str = "https://api.jup.ag/tokens/v2";

pub struct JupiterProvider {
    client: reqwest::Client,
    top_count: usize,
}

#[derive(Clone, Debug, Deserialize)]
struct JupiterToken {
    id: String,
    icon: Option<String>,
}

impl JupiterProvider {
    pub fn new(client: reqwest::Client, top_count: usize) -> Self {
        Self { client, top_count }
    }

    pub async fn get_verified_asset_images(&self) -> Result<Vec<AssetImage>, Box<dyn Error + Send + Sync>> {
        let tokens = self.get_tokens().await?;
        Ok(Self::map_tokens(tokens.into_iter().take(self.top_count).collect()))
    }

    pub async fn get_verified_asset_images_by_id(&self, token_id: &str) -> Result<Vec<AssetImage>, Box<dyn Error + Send + Sync>> {
        let tokens = self.get_tokens().await?;
        Ok(Self::map_tokens(tokens).into_iter().filter(|image| image.token_id == token_id).collect())
    }

    async fn get_tokens(&self) -> Result<Vec<JupiterToken>, Box<dyn Error + Send + Sync>> {
        let request = self.client.get(format!("{JUPITER_API_BASE_URL}/tag")).query(&[("query", "verified")]);
        let response = request.send().await?;
        let status = response.status();
        if !status.is_success() {
            return Err(format!("jupiter request failed: {status}").into());
        }
        Ok(response.json::<Vec<JupiterToken>>().await?)
    }

    fn map_tokens(tokens: Vec<JupiterToken>) -> Vec<AssetImage> {
        tokens
            .into_iter()
            .filter_map(|token| {
                let image_url = token.icon?;
                Some(AssetImage {
                    chain: Chain::Solana,
                    token_id: token.id,
                    image_url,
                })
            })
            .collect()
    }
}
