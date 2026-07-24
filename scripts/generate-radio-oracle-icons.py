#!/usr/bin/env python3
from __future__ import annotations

import struct
import subprocess
from pathlib import Path
from shutil import which

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"
DESKTOP_RESOURCE = ROOT / "desktopApp" / "src" / "main" / "resources" / "radio-oracle-logo.png"
PACKAGING_DIR = ROOT / "desktopApp" / "packaging" / "icons"
MAC_ICONSET_DIR = PACKAGING_DIR / "Radio-Oracle.iconset"
MAC_SERIES_ICONSET_DIR = PACKAGING_DIR / "Radio-Oracle-Series.iconset"
ROOT_ICON = ROOT / "icon.png"
JDEPLOY_BUNDLE_ICON = ROOT / "jdeploy-bundle" / "icon.png"
MAC_ICON = PACKAGING_DIR / "Radio-Oracle.icns"
MAC_SERIES_ICON = PACKAGING_DIR / "Radio-Oracle-Series.icns"
WINDOWS_ICON = PACKAGING_DIR / "Radio-Oracle.ico"
WINDOWS_SERIES_ICON = PACKAGING_DIR / "Radio-Oracle-Series.ico"
LINUX_ICON = PACKAGING_DIR / "Radio-Oracle.png"
LINUX_SERIES_ICON = PACKAGING_DIR / "Radio-Oracle-Series.png"
WINDOWS_ICON_SIZES = (16, 24, 32, 48, 64, 128, 256)

BG_TOP = (12, 54, 67)
BG_BOTTOM = (13, 104, 91)
INK = (248, 252, 249)
MIST = (196, 229, 219)
ORANGE = (244, 154, 28)
SHADOW = (5, 20, 23, 82)
DEEP = (9, 43, 52)
TEAL = (16, 86, 82)


def lerp(a: int, b: int, t: float) -> int:
    return int(a + (b - a) * t)


def scaled(size: int, value: float) -> int:
    return round(size * value / 512)


def line_width(size: int, value: float) -> int:
    return max(1, scaled(size, value))


def draw_background(draw: ImageDraw.ImageDraw, size: int) -> None:
    for y in range(size):
        t = y / max(1, size - 1)
        color = tuple(lerp(BG_TOP[i], BG_BOTTOM[i], t) for i in range(3))
        draw.line([(0, y), (size, y)], fill=color)


def draw_oracle_mark(draw: ImageDraw.ImageDraw, size: int, include_shadow: bool) -> None:
    def p(x: float, y: float) -> tuple[int, int]:
        return (scaled(size, x), scaled(size, y))

    if include_shadow:
        draw.rounded_rectangle(
            [
                scaled(size, 154),
                scaled(size, 256),
                scaled(size, 357),
                scaled(size, 459),
            ],
            radius=scaled(size, 23),
            fill=SHADOW,
        )

    # Oracle eye/crystal-ball transmitter. The eye shape carries the "oracle"
    # cue while the waves keep the Radio-O purpose visible at small sizes.
    eye_box = [
        scaled(size, 188),
        scaled(size, 104),
        scaled(size, 324),
        scaled(size, 200),
    ]
    draw.ellipse(eye_box, fill=INK)
    draw.polygon([p(185, 152), p(256, 100), p(327, 152), p(256, 204)], fill=INK)
    draw.ellipse(
        [
            scaled(size, 226),
            scaled(size, 122),
            scaled(size, 286),
            scaled(size, 182),
        ],
        fill=TEAL,
    )
    draw.ellipse(
        [
            scaled(size, 241),
            scaled(size, 137),
            scaled(size, 271),
            scaled(size, 167),
        ],
        fill=DEEP,
    )
    draw.ellipse(
        [
            scaled(size, 268),
            scaled(size, 122),
            scaled(size, 281),
            scaled(size, 135),
        ],
        fill=ORANGE,
    )
    draw.line([p(256, 196), p(256, 286)], fill=INK, width=line_width(size, 20))

    for inner, outer in ((64, 99), (106, 145)):
        draw.arc(
            [
                scaled(size, 256 - outer),
                scaled(size, 150 - outer),
                scaled(size, 256 + outer),
                scaled(size, 150 + outer),
            ],
            start=132,
            end=214,
            fill=MIST,
            width=line_width(size, 15),
        )
        draw.arc(
            [
                scaled(size, 256 - outer),
                scaled(size, 150 - outer),
                scaled(size, 256 + outer),
                scaled(size, 150 + outer),
            ],
            start=326,
            end=48,
            fill=MIST,
            width=line_width(size, 15),
        )

    # Orienteering control flag, simplified for launcher-icon scale.
    draw.rounded_rectangle(
        [
            scaled(size, 154),
            scaled(size, 275),
            scaled(size, 358),
            scaled(size, 459),
        ],
        radius=scaled(size, 22),
        fill=INK,
    )
    draw.rounded_rectangle(
        [
            scaled(size, 183),
            scaled(size, 302),
            scaled(size, 329),
            scaled(size, 430),
        ],
        radius=scaled(size, 7),
        fill=DEEP,
    )
    draw.polygon([p(183, 430), p(329, 302), p(329, 430)], fill=ORANGE)
    draw.polygon([p(183, 302), p(329, 302), p(183, 430)], fill=INK)

    # Start-finish/target dot reinforces the race-result role at tiny sizes.
    draw.ellipse(
        [
            scaled(size, 225),
            scaled(size, 346),
            scaled(size, 287),
            scaled(size, 408),
        ],
        fill=DEEP,
        outline=INK,
        width=line_width(size, 10),
    )

    # Small prediction/star cue, restrained so it survives masking but does not
    # become a text-like glyph.
    draw.polygon(
        [p(351, 223), p(361, 245), p(384, 254), p(361, 263), p(351, 286), p(341, 263), p(318, 254), p(341, 245)],
        fill=ORANGE,
    )


def rounded_alpha(size: int, radius: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def render_full_icon(size: int, rounded: bool = False) -> Image.Image:
    scale = 4
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    draw_background(draw, big)
    draw_oracle_mark(draw, big, include_shadow=True)
    img = img.resize((size, size), Image.Resampling.LANCZOS)
    if rounded:
        img.putalpha(rounded_alpha(size, round(size * 0.21)))
    return img


def render_foreground(size: int) -> Image.Image:
    scale = 4
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    draw_oracle_mark(draw, big, include_shadow=False)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def render_series_document_icon(size: int) -> Image.Image:
    """Precompose a framed series-document icon for desktop file associations."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    icon_size = round(size * 0.82)
    icon_x = (size - icon_size) // 2
    icon_y = round(size * 0.08)
    icon = render_full_icon(icon_size, rounded=True)

    shadow_alpha = icon.getchannel("A").filter(
        ImageFilter.GaussianBlur(radius=max(1, round(size * 0.025)))
    )
    shadow_alpha = shadow_alpha.point(lambda alpha: round(alpha * 0.42))
    shadow = Image.new("RGBA", icon.size, (0, 0, 0, 0))
    shadow.putalpha(shadow_alpha)
    canvas.alpha_composite(
        shadow,
        (icon_x, icon_y + round(size * 0.025)),
    )
    canvas.alpha_composite(icon, (icon_x, icon_y))

    frame = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    frame_draw = ImageDraw.Draw(frame, "RGBA")
    frame_draw.rounded_rectangle(
        [
            icon_x,
            icon_y,
            icon_x + icon_size - 1,
            icon_y + icon_size - 1,
        ],
        radius=round(icon_size * 0.21),
        outline=(235, 255, 252, 105),
        width=max(1, round(size * 0.008)),
    )
    canvas.alpha_composite(frame)
    return canvas


def save_webp(path: Path, image: Image.Image) -> None:
    image.save(path, "WEBP", quality=95, method=6)


def write_ico(path: Path, images: list[tuple[int, Image.Image]]) -> None:
    encoded_images = []
    for size, image in images:
        temp_path = path.with_name(f".ico-{size}.png")
        image.save(temp_path, "PNG")
        encoded_images.append((size, temp_path.read_bytes()))
        temp_path.unlink()

    header = struct.pack("<HHH", 0, 1, len(encoded_images))
    entries = bytearray()
    payload = bytearray()
    offset = 6 + 16 * len(encoded_images)
    for size, png_data in encoded_images:
        dimension = 0 if size >= 256 else size
        entries.extend(
            struct.pack(
                "<BBBBHHII",
                dimension,
                dimension,
                0,
                0,
                1,
                32,
                len(png_data),
                offset,
            )
        )
        payload.extend(png_data)
        offset += len(png_data)

    path.write_bytes(header + entries + payload)


def windows_icon_images(master: Image.Image) -> list[tuple[int, Image.Image]]:
    return [
        (size, master.resize((size, size), Image.Resampling.LANCZOS))
        for size in WINDOWS_ICON_SIZES
    ]


def write_macos_icns(path: Path, iconset_dir: Path, fallback_master: Image.Image) -> None:
    iconutil = which("iconutil")
    if iconutil is not None:
        try:
            subprocess.run(
                [iconutil, "-c", "icns", str(iconset_dir), "-o", str(path)],
                check=True,
                capture_output=True,
            )
            return
        except subprocess.CalledProcessError:
            pass

    # Pillow emits the modern ic07-ic14 chunk set, including the ic11
    # 16-point Retina representation Finder uses in list view. Writing PNG
    # payloads into the older icp4-icp6 chunks produces a valid-looking ICNS
    # whose small representations decode as colored noise on current macOS.
    fallback_master.save(path, "ICNS")


def write_iconset(
    iconset_dir: Path,
    master: Image.Image,
) -> None:
    iconset_dir.mkdir(parents=True, exist_ok=True)
    iconset_sizes = {
        "icon_16x16.png": 16,
        "icon_16x16@2x.png": 32,
        "icon_32x32.png": 32,
        "icon_32x32@2x.png": 64,
        "icon_128x128.png": 128,
        "icon_128x128@2x.png": 256,
        "icon_256x256.png": 256,
        "icon_256x256@2x.png": 512,
        "icon_512x512.png": 512,
        "icon_512x512@2x.png": 1024,
    }
    for filename, size in iconset_sizes.items():
        master.resize((size, size), Image.Resampling.LANCZOS).save(iconset_dir / filename)


def write_desktop_packaging_icons(
    master: Image.Image,
    mac_master: Image.Image,
    series_master: Image.Image,
) -> None:
    PACKAGING_DIR.mkdir(parents=True, exist_ok=True)
    write_iconset(MAC_ICONSET_DIR, mac_master)
    write_iconset(MAC_SERIES_ICONSET_DIR, series_master)
    write_macos_icns(MAC_ICON, MAC_ICONSET_DIR, mac_master)
    write_macos_icns(MAC_SERIES_ICON, MAC_SERIES_ICONSET_DIR, series_master)
    write_ico(WINDOWS_ICON, windows_icon_images(master))
    write_ico(WINDOWS_SERIES_ICON, windows_icon_images(series_master))
    master.resize((512, 512), Image.Resampling.LANCZOS).save(LINUX_ICON)
    series_master.resize((512, 512), Image.Resampling.LANCZOS).save(LINUX_SERIES_ICON)


def main() -> None:
    density_sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    foreground_sizes = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }

    for folder, size in density_sizes.items():
        out_dir = RES / folder
        render_full_icon(size).save(out_dir / "ic_logo.png")
        render_full_icon(size, rounded=True).save(out_dir / "ic_logo_round.png")
        save_webp(out_dir / "ic_launcher.webp", render_full_icon(size))
        save_webp(out_dir / "ic_launcher_round.webp", render_full_icon(size, rounded=True))

    for folder, size in foreground_sizes.items():
        render_foreground(size).save(RES / folder / "ic_runner_foreground.png")

    play_icon = render_full_icon(512)
    desktop_launcher_icon = render_full_icon(512, rounded=True)
    play_icon.save(ROOT / "app" / "src" / "main" / "ic_runner-playstore.png")
    play_icon.save(DESKTOP_RESOURCE)
    desktop_launcher_icon.save(ROOT_ICON)
    desktop_launcher_icon.save(JDEPLOY_BUNDLE_ICON)
    write_desktop_packaging_icons(
        render_full_icon(1024),
        mac_master=render_full_icon(1024, rounded=True),
        series_master=render_series_document_icon(1024),
    )


if __name__ == "__main__":
    main()
