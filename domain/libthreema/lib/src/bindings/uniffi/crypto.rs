use crate::{
    common::Nonce,
    crypto::{
        aead::AeadInPlace as _, blake2b, chacha20, cipher::KeyInit as _, digest::FixedOutput as _,
        salsa20, x25519,
    },
};

/// A general crypto-related error.
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CryptoError {
    /// Invalid parameter provided by foreign code.
    #[cfg(feature = "uniffi")]
    #[error("Invalid parameter: {0}")]
    InvalidParameter(&'static str),

    /// Unable to encrypt/decrypt.
    #[error("Unable to encrypt/decrypt")]
    CipherFailed,
}

/// Derive a Blake2b MAC from the provided key, personal and salt.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is present and less than 1 or more than 64
/// bytes and when `personal` or `salt` is more than 8 bytes.
#[uniffi::export]
pub fn blake2b_mac_256(
    key: &Option<Vec<u8>>,
    personal: &[u8],
    salt: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    let mac = blake2b::Blake2bMac256::new_with_salt_and_personal(key.as_deref(), salt, personal)
        .map_err(|_| {
            CryptoError::InvalidParameter(
                "'key' if provided must be between 1 and 64 bytes, 'personal' and 'salt' must be \
                 up to 8 bytes",
            )
        })?
        .finalize_fixed();
    Ok(mac.to_vec())
}

/// Derive the X25519 public key associated to the provided X25519 secret key.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `secret_key` is not exactly 32 bytes.
#[uniffi::export]
pub fn x25519_derive_public_key(secret_key: &[u8]) -> Result<Vec<u8>, CryptoError> {
    let secret_key = x25519::StaticSecret::from(
        <[u8; 32]>::try_from(secret_key)
            .map_err(|_| CryptoError::InvalidParameter("'secret_key' must be 32 bytes"))?,
    );
    let public_key = x25519::PublicKey::from(&secret_key);
    Ok(public_key.as_bytes().to_vec())
}

/// Derive the X25519 shared secret from the provided X25519 public and secret key.
///
/// Note: The resulting shared secret will be hashed with HSalsa20 to ensure uniform distribution.
/// This is compatible with classic NaCl implementations.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `public_key` or `secret_key` is not exactly 32
/// bytes.
#[uniffi::export]
pub fn x25519_hsalsa20_derive_shared_secret(
    public_key: &[u8],
    secret_key: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    let public_key = x25519::PublicKey::from(
        <[u8; 32]>::try_from(public_key)
            .map_err(|_| CryptoError::InvalidParameter("'public_key' must be 32 bytes"))?,
    );
    let secret_key = x25519::StaticSecret::from(
        <[u8; 32]>::try_from(secret_key)
            .map_err(|_| CryptoError::InvalidParameter("'secret_key' must be 32 bytes"))?,
    );
    let shared_secret = x25519::SharedSecretHSalsa20::from(secret_key.diffie_hellman(&public_key));
    Ok(shared_secret.as_bytes().to_vec())
}

/// Encrypt the provided data using XChaCha20 and append a Poly1305 MAC.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if encryption failed.
#[uniffi::export]
pub fn xchacha20_poly1305_encrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
    associated_data: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    let cipher = chacha20::XChaCha20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes"))?;
    cipher
        .encrypt_in_place((&nonce).into(), associated_data, &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

/// Decrypt the provided data using XChaCha20.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if decryption failed.
#[uniffi::export]
pub fn xchacha20_poly1305_decrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
    associated_data: &[u8],
) -> Result<Vec<u8>, CryptoError> {
    let cipher = chacha20::XChaCha20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes"))?;
    cipher
        .decrypt_in_place((&nonce).into(), associated_data, &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

/// Encrypt the provided data using XSalsa20 and append a Poly1305 MAC.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if encryption failed.
#[uniffi::export]
pub fn xsalsa20_poly1305_encrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
) -> Result<Vec<u8>, CryptoError> {
    let cipher = salsa20::XSalsa20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes"))?;
    cipher
        .encrypt_in_place((&nonce).into(), &[], &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}

/// Decrypt the provided data using XSalsa20.
///
/// # Errors
///
/// Returns [`CryptoError::InvalidParameter`] if `key` is not exactly 32 bytes or `nonce` is not
/// exactly 24 bytes.
///
/// Returns [`CryptoError::CipherFailed`] if decryption failed.
#[uniffi::export]
pub fn xsalsa20_poly1305_decrypt(
    key: &[u8],
    nonce: &[u8],
    mut data: Vec<u8>,
) -> Result<Vec<u8>, CryptoError> {
    let cipher = salsa20::XSalsa20Poly1305::new_from_slice(key)
        .map_err(|_| CryptoError::InvalidParameter("'key' must be 32 bytes"))?;
    let nonce: Nonce = nonce
        .try_into()
        .map_err(|_| CryptoError::InvalidParameter("'nonce' must be 24 bytes"))?;
    cipher
        .decrypt_in_place((&nonce).into(), &[], &mut data)
        .map_err(|_| CryptoError::CipherFailed)?;
    Ok(data)
}