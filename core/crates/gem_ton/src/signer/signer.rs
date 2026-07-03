use primitives::SignerError;
use signer::Ed25519KeyPair;

use super::transaction::WalletV5R1;
use crate::address::Address;

pub struct TonSigner {
    key_pair: Ed25519KeyPair,
    wallet: WalletV5R1,
}

impl TonSigner {
    pub fn new(private_key: &[u8]) -> Result<Self, SignerError> {
        let key_pair = Ed25519KeyPair::from_private_key(private_key)?;
        let wallet = WalletV5R1::new(key_pair.public_key_bytes)?;
        Ok(Self { key_pair, wallet })
    }

    pub fn wallet(&self) -> &WalletV5R1 {
        &self.wallet
    }

    pub fn address(&self) -> &Address {
        &self.wallet.address
    }

    pub fn public_key(&self) -> [u8; 32] {
        self.key_pair.public_key_bytes
    }

    pub fn sign(&self, digest: &[u8]) -> [u8; 64] {
        self.key_pair.sign(digest)
    }
}

pub struct TonSignResult {
    pub signature: Vec<u8>,
    pub public_key: Vec<u8>,
    pub timestamp: u64,
}
