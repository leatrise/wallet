use gem_keystore::Mnemonic;
use primitives::{Account, BitcoinChain, Chain};
use zeroize::Zeroizing;

use crate::AccountDerivationError;
use crate::private_key::{default_derivation_path, derive_account_from_private_key};

use super::{
    bip32, bitcoin, cardano,
    scheme::{DerivationScheme, derivation_scheme},
    slip10,
    ton,
};

struct DerivedPrivateKey {
    private_key: Zeroizing<Vec<u8>>,
    extended_public_key: Option<String>,
}

pub fn derive_accounts_from_mnemonic(phrase: &str, chains: Vec<Chain>) -> Result<Vec<Account>, AccountDerivationError> {
    if chains.is_empty() {
        return Err(AccountDerivationError::invalid_input("mnemonic derivation requires at least one chain"));
    }

    let chains = unique_chains(chains);
    let seed = match Mnemonic::seed(phrase) {
        Ok(seed) => seed,
        Err(error) => {
            if chains.len() == 1 && chains[0] == Chain::Ton && ton::is_valid(phrase) {
                let private_key = ton::derive_private_key(phrase)?;
                return Ok(vec![derive_account_from_private_key(&private_key, Chain::Ton)?]);
            }
            return Err(error.into());
        }
    };
    let entropy = Mnemonic::entropy(phrase)?;
    let mut accounts = Vec::with_capacity(chains.len());
    for chain in chains {
        let derived_private_key = derive_private_key_by_chain(seed.as_slice(), entropy.as_slice(), chain)?;
        let mut account = derive_account_from_private_key(&derived_private_key.private_key, chain)?;
        if let Some(extended_public_key) = derived_private_key.extended_public_key {
            account.extended_public_key = Some(extended_public_key);
        }
        accounts.push(account);
    }
    Ok(accounts)
}

pub fn derive_private_key_from_mnemonic(phrase: &str, chain: Chain) -> Result<Zeroizing<Vec<u8>>, AccountDerivationError> {
    let seed = match Mnemonic::seed(phrase) {
        Ok(seed) => seed,
        Err(error) => {
            if chain == Chain::Ton {
                return ton::derive_private_key(phrase);
            }
            return Err(error.into());
        }
    };
    let entropy = Mnemonic::entropy(phrase)?;
    derive_private_key_by_chain(seed.as_slice(), entropy.as_slice(), chain).map(|derived| derived.private_key)
}

pub fn is_ton_native_mnemonic(phrase: &str) -> bool {
    ton::is_valid(phrase)
}

fn unique_chains(chains: Vec<Chain>) -> Vec<Chain> {
    let mut unique = Vec::with_capacity(chains.len());
    for chain in chains {
        if !unique.contains(&chain) {
            unique.push(chain);
        }
    }
    unique
}

fn derive_private_key_by_chain(seed: &[u8], entropy: &[u8], chain: Chain) -> Result<DerivedPrivateKey, AccountDerivationError> {
    let (private_key, extended_public_key) = match derivation_scheme(chain)? {
        DerivationScheme::Bip44 | DerivationScheme::Bip84 => {
            let private_key = bip32::derive_secp256k1_private_key(seed, default_derivation_path(chain))?;
            let extended_public_key = BitcoinChain::from_chain(chain)
                .map(|bitcoin_chain| bitcoin::derive_extended_public_key(seed, bitcoin_chain))
                .transpose()?;
            (private_key, extended_public_key)
        }
        DerivationScheme::Slip10 => (slip10::derive_ed25519_private_key(seed, default_derivation_path(chain))?, None),
        DerivationScheme::Cardano => (cardano::derive_private_key(entropy)?, None),
    };
    Ok(DerivedPrivateKey { private_key, extended_public_key })
}
