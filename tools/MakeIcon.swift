import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Generates the Android adaptive-icon layers from the app artwork.
//
// Run as:  swift tools/MakeIcon.swift branding/play-store-icon-512.png app/src/main/res
//
// The layers in the tree were cut from the artwork at its native 1254px; the
// checked-in 512px copy is what survives in the repo, and at 512 it is still
// larger than the biggest plate any density needs (289px at xxxhdpi), so a
// regeneration from it matches. The sampled band colours shift by a few units,
// the bottom row having been resampled — the band sits outside every standard
// mask, so this is invisible.
//
// The artwork is a full-bleed square: an Aedes mosquito over a map of Bangladesh
// with a location pin, signal arcs sweeping out to the top right, and a blue
// wave across the bottom. Android masks an adaptive icon down to a 72dp circle
// inside a 108dp canvas, so the question is only how large the square may be
// before the mask starts eating it.
//
// Measured, not guessed: the furthest ink — the outer signal arc — sits at 1.04
// of the artwork's own half-width, because the arcs sweep around a pin that is
// left of centre. A plate of 67% of the canvas therefore puts that arc at a
// 70dp diameter, just inside the 72dp the launcher keeps, and the plate itself
// covers the mask exactly so no background shows at the edges. Earlier passes
// shipped 106%, 88% and 76%; every one of them cut the arcs off on the device.
//
//   foreground   the artwork, unaltered, at 67% of the canvas and centred
//   background   white above and the wave's own blue across the bottom 17%,
//                so the sliver a launcher reveals while it animates the icon is
//                the artwork's colour rather than a grey box
//
// The artwork is not cut out by alpha. It has its own white page which meets
// the white background without a seam, and an alpha cut-out of a design this
// intricate — the map's river deltas, the mosquito's legs — fringes badly.
//
// No monochrome layer is emitted. Android 13+ can tint a single-colour
// silhouette, but this artwork does not reduce to one: the map fills in solid
// and the result reads as a smudge, so the icon omits <monochrome> and
// launchers fall back to the full-colour version in themed mode.

/// The plate's share of the 108dp canvas. See the note above before changing it.
let plateFraction = 0.67

/// Where the background's blue band begins, which is the plate's bottom edge.
let bandTop = 0.5 + plateFraction / 2

/// Density buckets and the pixel size of a 108dp canvas in each.
let densities: [(name: String, size: Int)] = [
    ("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432),
]

let args = CommandLine.arguments
guard args.count >= 3 else {
    FileHandle.standardError.write("usage: MakeIcon <source.png> <res-dir>\n".data(using: .utf8)!)
    exit(2)
}
let sourcePath = args[1]
let resDir = args[2]

guard let source = NSImage(contentsOfFile: sourcePath),
      let art = source.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    FileHandle.standardError.write("cannot read \(sourcePath)\n".data(using: .utf8)!)
    exit(1)
}

let space = CGColorSpaceCreateDeviceRGB()

func context(_ size: Int) -> CGContext? {
    CGContext(
        data: nil, width: size, height: size,
        bitsPerComponent: 8, bytesPerRow: size * 4, space: space,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )
}

func write(_ image: CGImage, to path: String) {
    let url = URL(fileURLWithPath: path)
    guard let dest = CGImageDestinationCreateWithURL(
        url as CFURL, UTType.png.identifier as CFString, 1, nil
    ) else {
        FileHandle.standardError.write("cannot write \(path)\n".data(using: .utf8)!)
        exit(1)
    }
    CGImageDestinationAddImage(dest, image, nil)
    guard CGImageDestinationFinalize(dest) else {
        FileHandle.standardError.write("cannot finalise \(path)\n".data(using: .utf8)!)
        exit(1)
    }
}

// MARK: - The wave's colours
//
// Sampled from the artwork's own bottom row rather than typed in, so the band
// follows the artwork if it is ever redrawn.
//
// Two different vertical conventions meet here, and mixing them up silently
// produces an icon that is upside down, so both are stated. In the pixel buffer
// row 0 is the image's TOP — checked by reading this artwork, whose top is white
// and whose bottom is blue. In a CGContext's drawing coordinates y = 0 is the
// BOTTOM. Reads below use the first convention, draws the second.

let w = art.width, h = art.height
var pixels = [UInt8](repeating: 0, count: h * w * 4)
guard let readCtx = CGContext(
    data: &pixels, width: w, height: h,
    bitsPerComponent: 8, bytesPerRow: w * 4, space: space,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
) else { exit(1) }
readCtx.draw(art, in: CGRect(x: 0, y: 0, width: w, height: h))

func bottomRow(atFraction f: Double) -> CGColor {
    let x = min(w - 1, Int(f * Double(w)))
    let i = (h - 1) * w * 4 + x * 4                  // last buffer row = image bottom
    return CGColor(
        red: CGFloat(pixels[i]) / 255, green: CGFloat(pixels[i + 1]) / 255,
        blue: CGFloat(pixels[i + 2]) / 255, alpha: 1
    )
}
let bandStops = [bottomRow(atFraction: 0), bottomRow(atFraction: 0.5), bottomRow(atFraction: 0.999)]

/// The artwork's page, read from a corner well clear of any ink.
let page: CGColor = {
    let i = 0                                        // first buffer row = image top
    return CGColor(
        red: CGFloat(pixels[i]) / 255, green: CGFloat(pixels[i + 1]) / 255,
        blue: CGFloat(pixels[i + 2]) / 255, alpha: 1
    )
}()

// MARK: - Draw

for (name, size) in densities {
    let side = Double(size)

    guard let fg = context(size) else { exit(1) }
    let plate = side * plateFraction
    let offset = (side - plate) / 2
    fg.interpolationQuality = .high
    fg.draw(art, in: CGRect(x: offset, y: offset, width: plate, height: plate))
    guard let fgImage = fg.makeImage() else { exit(1) }
    write(fgImage, to: "\(resDir)/mipmap-\(name)/ic_launcher_foreground.png")

    guard let bg = context(size) else { exit(1) }
    bg.setFillColor(page)
    bg.fill(CGRect(x: 0, y: 0, width: side, height: side))
    // Drawing y = 0 is the bottom, so the band runs from the origin up to where
    // the plate's bottom edge falls.
    let bandHeight = side * (1 - bandTop)
    guard let gradient = CGGradient(
        colorsSpace: space, colors: bandStops as CFArray, locations: [0, 0.5, 1]
    ) else { exit(1) }
    bg.saveGState()
    bg.clip(to: CGRect(x: 0, y: 0, width: side, height: bandHeight))
    bg.drawLinearGradient(
        gradient,
        start: CGPoint(x: 0, y: 0), end: CGPoint(x: side, y: 0),
        options: []
    )
    bg.restoreGState()
    guard let bgImage = bg.makeImage() else { exit(1) }
    write(bgImage, to: "\(resDir)/mipmap-\(name)/ic_launcher_background.png")

    print("\(name): \(size)px canvas, plate \(Int(plate.rounded()))px")
}
