use image::{
    ColorType, DynamicImage, GenericImage, GenericImageView, ImageBuffer, ImageEncoder, RgbaImage,
    codecs::png::{CompressionType, FilterType, PngEncoder},
    imageops,
};
use resvg::{tiny_skia, usvg};
use std::{cmp::min, error::Error, io::Cursor};

pub async fn download_image(client: &reqwest::Client, url: &str, image_size: u32) -> Result<Vec<u8>, Box<dyn Error + Send + Sync>> {
    let response = client.get(url).send().await?;
    if response.status() != 200 {
        return Err("<== image not found".into());
    }
    let content_type = response
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .map(|value| value.to_string());
    let bytes = response.bytes().await?;
    format_image(url, content_type.as_deref(), bytes.as_ref(), image_size)
}

fn format_image(url: &str, content_type: Option<&str>, bytes: &[u8], image_size: u32) -> Result<Vec<u8>, Box<dyn Error + Send + Sync>> {
    let image = if is_svg(url, content_type, bytes) {
        decode_svg(bytes)?
    } else {
        image::load_from_memory(bytes)?
    };
    encode_png(resize_image(image, image_size)?, image_size)
}

fn is_svg(url: &str, content_type: Option<&str>, bytes: &[u8]) -> bool {
    content_type.is_some_and(|content_type| content_type.contains("svg"))
        || url.to_lowercase().split('?').next().is_some_and(|path| path.ends_with(".svg"))
        || std::str::from_utf8(&bytes[..min(bytes.len(), 128)]).is_ok_and(|prefix| prefix.contains("<svg"))
}

fn decode_svg(bytes: &[u8]) -> Result<DynamicImage, Box<dyn Error + Send + Sync>> {
    let tree = usvg::Tree::from_data(bytes, &usvg::Options::default())?;
    let size = tree.size().to_int_size();
    let mut pixmap = tiny_skia::Pixmap::new(size.width(), size.height()).ok_or("invalid svg size")?;
    resvg::render(&tree, tiny_skia::Transform::default(), &mut pixmap.as_mut());
    let image = RgbaImage::from_raw(size.width(), size.height(), pixmap.data().to_vec()).ok_or("invalid svg image")?;
    Ok(DynamicImage::ImageRgba8(image))
}

fn resize_image(image: DynamicImage, image_size: u32) -> Result<RgbaImage, Box<dyn Error + Send + Sync>> {
    let (width, height) = image.dimensions();
    if image_size == 0 || width == 0 || height == 0 {
        return Err("invalid image size".into());
    }

    let scale = (image_size as f32 / width as f32).min(image_size as f32 / height as f32);
    let resized_width = ((width as f32 * scale).round() as u32).max(1);
    let resized_height = ((height as f32 * scale).round() as u32).max(1);
    let resized = imageops::resize(&image.to_rgba8(), resized_width, resized_height, imageops::FilterType::Lanczos3);
    let mut output = ImageBuffer::from_pixel(image_size, image_size, image::Rgba([0, 0, 0, 0]));
    output.copy_from(&resized, (image_size - resized_width) / 2, (image_size - resized_height) / 2)?;
    Ok(output)
}

fn encode_png(image: RgbaImage, image_size: u32) -> Result<Vec<u8>, Box<dyn Error + Send + Sync>> {
    let mut output = Cursor::new(Vec::new());
    let encoder = PngEncoder::new_with_quality(&mut output, CompressionType::Best, FilterType::Adaptive);
    encoder.write_image(image.as_raw(), image_size, image_size, ColorType::Rgba8.into())?;
    compress_png(output.into_inner())
}

fn compress_png(bytes: Vec<u8>) -> Result<Vec<u8>, Box<dyn Error + Send + Sync>> {
    let options = oxipng::Options::from_preset(4);
    Ok(oxipng::optimize_from_memory(&bytes, &options)?)
}
