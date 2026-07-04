use gem_crypto::{hash::hmac_sha512, pbkdf::pbkdf2_hmac_sha512};
use gem_keystore::Mnemonic;
use zeroize::Zeroizing;

use crate::AccountDerivationError;

const WORD_COUNT: usize = 24;
const PBKDF_ITERATIONS: usize = 100_000;
const SEED_LEN: usize = 64;
const PRIVATE_KEY_LEN: usize = 32;
const BASIC_SEED_ITERATIONS: usize = PBKDF_ITERATIONS / 256;
const BASIC_SEED_SALT: &[u8] = b"TON seed version";
const DEFAULT_SEED_SALT: &[u8] = b"TON default seed";

pub(super) fn is_valid(phrase: &str) -> bool {
    clean_phrase(phrase)
        .and_then(|phrase| entropy(&phrase))
        .and_then(|entropy| is_basic_seed(entropy.as_slice()))
        .unwrap_or(false)
}

pub(super) fn derive_private_key(phrase: &str) -> Result<Zeroizing<Vec<u8>>, AccountDerivationError> {
    let phrase = clean_phrase(phrase)?;
    let entropy = entropy(&phrase)?;
    if !is_basic_seed(entropy.as_slice())? {
        return Err(AccountDerivationError::invalid_input("mnemonic"));
    }
    let seed = pbkdf2_hmac_sha512(entropy.as_slice(), DEFAULT_SEED_SALT, PBKDF_ITERATIONS, SEED_LEN)?;
    Ok(Zeroizing::new(seed[..PRIVATE_KEY_LEN].to_vec()))
}

fn clean_phrase(phrase: &str) -> Result<Zeroizing<String>, AccountDerivationError> {
    let phrase = Mnemonic::normalize_words(phrase)?;
    if phrase.split_whitespace().count() != WORD_COUNT {
        return Err(AccountDerivationError::invalid_input("mnemonic"));
    }
    Ok(phrase)
}

fn entropy(phrase: &str) -> Result<Zeroizing<[u8; SEED_LEN]>, AccountDerivationError> {
    Ok(hmac_sha512(phrase.as_bytes(), b"")?)
}

fn is_basic_seed(entropy: &[u8]) -> Result<bool, AccountDerivationError> {
    let seed = pbkdf2_hmac_sha512(entropy, BASIC_SEED_SALT, BASIC_SEED_ITERATIONS.max(1), SEED_LEN)?;
    Ok(seed[0] == 0)
}

#[cfg(test)]
mod tests {
    use primitives::hex;

    use super::*;

    const PHRASE: &str =
        "attitude monster voyage sing roof install lobster identify slot trophy degree spin rude song town run cost rally blossom twice tank number pioneer myth";

    #[test]
    fn test_ton_native_mnemonic() {
        assert!(is_valid(PHRASE));
        assert!(!Mnemonic::is_valid(PHRASE));

        let private_key = derive_private_key(PHRASE).unwrap();
        assert_eq!(hex::encode(private_key.as_slice()), "cb3d3f3045ed8ccad0acda6ff5ef0b5370381590b66d68035f268ec0216fa7cb");
    }

    #[test]
    fn test_ton_native_mnemonic_rejects_invalid_input() {
        assert!(!is_valid("abandon abandon"));
        assert!(!is_valid(&PHRASE.replace("attitude", "invalid")));
        assert!(derive_private_key("abandon abandon").is_err());
    }
}
