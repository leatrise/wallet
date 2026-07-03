use primitives::ImageType;
use std::{error::Error, fmt};

#[derive(Debug)]
pub enum ImageDownloadError {
    UnsupportedType(Option<ImageType>),
}

impl fmt::Display for ImageDownloadError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::UnsupportedType(Some(image_type)) => write!(formatter, "unsupported image type: {}", image_type.extension()),
            Self::UnsupportedType(None) => write!(formatter, "unsupported image type"),
        }
    }
}

impl Error for ImageDownloadError {}
