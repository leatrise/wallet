use serde::{Deserialize, Serialize};

pub const MIME_TYPE_PNG: &str = "image/png";
pub const MIME_TYPE_JPEG: &str = "image/jpeg";
pub const MIME_TYPE_SVG: &str = "image/svg+xml";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ImageType {
    Jpeg,
    Png,
    Svg,
}

struct ImageTypeInfo {
    image_type: ImageType,
    labels: &'static [&'static str],
    mime_type: &'static str,
    extension: &'static str,
    magic_bytes: &'static [&'static [u8]],
}

const IMAGE_TYPES: &[ImageTypeInfo] = &[
    ImageTypeInfo {
        image_type: ImageType::Jpeg,
        labels: &["jpg", "jpeg"],
        mime_type: MIME_TYPE_JPEG,
        extension: "jpg",
        magic_bytes: &[&[0xFF, 0xD8, 0xFF]],
    },
    ImageTypeInfo {
        image_type: ImageType::Png,
        labels: &["png"],
        mime_type: MIME_TYPE_PNG,
        extension: "png",
        magic_bytes: &[b"\x89PNG\r\n\x1A\n"],
    },
    ImageTypeInfo {
        image_type: ImageType::Svg,
        labels: &["svg"],
        mime_type: MIME_TYPE_SVG,
        extension: "svg",
        magic_bytes: &[b"<svg"],
    },
];

impl ImageType {
    pub fn from_label(value: &str) -> Option<Self> {
        let value = value.to_ascii_lowercase();
        IMAGE_TYPES.iter().find(|info| info.labels.contains(&value.as_str())).map(|info| info.image_type)
    }

    pub fn from_mime_type(value: &str) -> Option<Self> {
        let value = value.split(';').next().unwrap_or(value).trim();
        IMAGE_TYPES.iter().find(|info| info.mime_type == value).map(|info| info.image_type)
    }

    pub fn from_extension(file_name: &str) -> Option<Self> {
        Self::from_label(file_name.rsplit_once('.')?.1)
    }

    pub fn from_magic_bytes(data: &[u8]) -> Option<Self> {
        IMAGE_TYPES
            .iter()
            .find(|info| info.magic_bytes.iter().any(|magic_bytes| data.starts_with(magic_bytes)))
            .map(|info| info.image_type)
    }

    pub fn mime_type(self) -> &'static str {
        self.info().mime_type
    }

    pub fn extension(self) -> &'static str {
        self.info().extension
    }

    fn info(self) -> &'static ImageTypeInfo {
        IMAGE_TYPES.iter().find(|info| info.image_type == self).unwrap()
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
