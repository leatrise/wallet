use serde::{Deserialize, Serialize};
use std::str::FromStr;
use strum::{AsRefStr, EnumString};

pub const MIME_TYPE_PNG: &str = "image/png";
pub const MIME_TYPE_JPEG: &str = "image/jpeg";
pub const MIME_TYPE_SVG: &str = "image/svg+xml";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, AsRefStr, EnumString)]
#[serde(rename_all = "lowercase")]
#[strum(serialize_all = "lowercase")]
pub enum ImageType {
    #[strum(serialize = "jpg", serialize = "jpeg")]
    Jpeg,
    Png,
    Svg,
}

impl ImageType {
    pub fn from_label(value: &str) -> Option<Self> {
        Self::from_str(&value.to_ascii_lowercase()).ok()
    }

    pub fn from_mime_type(value: &str) -> Option<Self> {
        let value = value.split(';').next().unwrap_or(value).trim();
        match value {
            MIME_TYPE_JPEG => Some(Self::Jpeg),
            MIME_TYPE_PNG => Some(Self::Png),
            MIME_TYPE_SVG => Some(Self::Svg),
            _ => None,
        }
    }

    pub fn from_extension(file_name: &str) -> Option<Self> {
        Self::from_label(file_name.rsplit_once('.')?.1)
    }

    pub fn from_magic_bytes(data: &[u8]) -> Option<Self> {
        if data.starts_with(&[0xFF, 0xD8, 0xFF]) {
            return Some(Self::Jpeg);
        }
        if data.starts_with(b"\x89PNG\r\n\x1A\n") {
            return Some(Self::Png);
        }
        if data.starts_with(b"<svg") {
            return Some(Self::Svg);
        }
        None
    }

    pub fn mime_type(self) -> &'static str {
        match self {
            Self::Jpeg => MIME_TYPE_JPEG,
            Self::Png => MIME_TYPE_PNG,
            Self::Svg => MIME_TYPE_SVG,
        }
    }

    pub fn extension(self) -> String {
        self.as_ref().to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_common_labels() {
        assert_eq!(ImageType::from_label("jpg"), Some(ImageType::Jpeg));
        assert_eq!(ImageType::from_label("jpeg"), Some(ImageType::Jpeg));
        assert_eq!(ImageType::from_label("png"), Some(ImageType::Png));
        assert_eq!(ImageType::from_label("html"), None);
    }

    #[test]
    fn parses_mime_types() {
        assert_eq!(ImageType::from_mime_type(MIME_TYPE_JPEG), Some(ImageType::Jpeg));
        assert_eq!(ImageType::from_mime_type(MIME_TYPE_PNG), Some(ImageType::Png));
        assert_eq!(ImageType::from_mime_type(MIME_TYPE_SVG), Some(ImageType::Svg));
        assert_eq!(ImageType::from_mime_type("text/html"), None);
    }

    #[test]
    fn detects_image_signatures() {
        assert_eq!(ImageType::from_magic_bytes(&[0xFF, 0xD8, 0xFF]), Some(ImageType::Jpeg));
        assert_eq!(ImageType::from_magic_bytes(b"\x89PNG\r\n\x1A\nrest"), Some(ImageType::Png));
        assert_eq!(ImageType::from_magic_bytes(b"<html></html>"), None);
    }
}
