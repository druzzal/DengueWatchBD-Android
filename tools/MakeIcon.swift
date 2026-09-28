import AppKit
import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Generates Android adaptive-icon layers from the iOS AppIcon artwork.
//
// The iOS icon is a full-bleed square: an Aedes mosquito and a map of Bangladesh
// on a red field. Android masks an adaptive icon down to a 66dp circle inside a
// 108dp canvas, so dropping the square in whole would slice the mosquito's wings
// and legs off on any launcher using a round mask.
//
// The artwork is therefore separated by role rather than by rectangle:
//
//   background   the red field, sampled from the original and redrawn as a
//                gradient, filling the canvas edge to edge so it takes the crop
//   foreground   the mosquito and map alone, cut out by alpha and scaled so the
//                furthest subject pixel lands inside the guaranteed-safe circle

// No monochrome layer is emitted. Android 13+ can tint a single-colour
// silhouette, but this artwork does not reduce to one: the white map fills in
// solid and the result reads as a smudge, so the icon omits <monochrome> and
// launchers fall back to the full-colour version in themed mode.
//
// Cutting the subject out by alpha rather than shipping a cropped rectangle
// matters: a rectangle carries its own slice of the original gradient, which
// does not line up with the reconstructed one behind it and reads as a visible
// seam boxing in the artwork.
//
// A note on orientation, established empirically rather than assumed: in a
// CGBitmapContext built this way, buffer row 0 is the *bottom* of the image.
// Cross-checked by cropping the real top and bottom strips with sips, which
// works in image space with the origin at top-left, and comparing averages.

let args = CommandLine.arguments
guard args.count >= 3 else {
    FileHandle.standardError.write("usage: MakeIcon <source.png> <res-dir>\n".data(using: .utf8)!)
    exit(2)
}
let sourcePath = args[1]
let resDir = args[2]

guard let source = NSImage(contentsOfFile: sourcePath),
      let sourceCG = source.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
    FileHandle.standardError.write("cannot read \(sourcePath)\n".data(using: .utf8)!)
    exit(1)
}

let w = sourceCG.width
let h = sourceCG.height
let space = CGColorSpaceCreateDeviceRGB()
let bytesPerRow = w * 4

var pixels = [UInt8](repeating: 0, count: h * bytesPerRow)
guard let readCtx = CGContext(
    data: &pixels, width: w, height: h,
    bitsPerComponent: 8, bytesPerRow: bytesPerRow, space: space,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
) else { exit(1) }
readCtx.draw(sourceCG, in: CGRect(x: 0, y: 0, width: w, height: h))

func px(_ x: Int, _ y: Int) -> (r: Double, g: Double, b: Double) {
    let i = y * bytesPerRow + x * 4
    return (Double(pixels[i]), Double(pixels[i + 1]), Double(pixels[i + 2]))
}

// MARK: - Sample the background field
//
// Buffer row 0 is the image's bottom, so low-y samples describe the bottom of
// the artwork and high-y samples its top.

func average(_ points: [(Int, Int)]) -> (Double, Double, Double) {
    var r = 0.0, g = 0.0, b = 0.0
    for (x, y) in points {
        let c = px(x, y)
        r += c.r; g += c.g; b += c.b
    }
    let n = Double(points.count)
    return (r / n, g / n, b / n)
}

let inset = w / 40
let fieldAtImageBottom = average([
    (inset, inset), (w / 2, inset), (w - inset, inset),
    (inset, h / 10), (w - inset, h / 10),
])
let fieldAtImageTop = average([
    (inset, h - inset), (w / 2, h - inset), (w - inset, h - inset),
    (inset, h - h / 10), (w - inset, h - h / 10),
])

func hex(_ c: (Double, Double, Double)) -> String {
    String(format: "#%02X%02X%02X", Int(c.0.rounded()), Int(c.1.rounded()), Int(c.2.rounded()))
}
print("field at image top    \(hex(fieldAtImageTop))")
print("field at image bottom \(hex(fieldAtImageBottom))")

/// How far a pixel sits from the background field at its own height.
/// Drives both subject detection and the alpha cut-out.
func backgroundDistance(_ x: Int, _ y: Int) -> Double {
    let c = px(x, y)
    let t = Double(y) / Double(h - 1)   // 0 at image bottom, 1 at image top
    let ref = (
        fieldAtImageBottom.0 + (fieldAtImageTop.0 - fieldAtImageBottom.0) * t,
        fieldAtImageBottom.1 + (fieldAtImageTop.1 - fieldAtImageBottom.1) * t,
        fieldAtImageBottom.2 + (fieldAtImageTop.2 - fieldAtImageBottom.2) * t
    )
    let dr = c.r - ref.0, dg = c.g - ref.1, db = c.b - ref.2
    return (dr * dr + dg * dg + db * db).squareRoot()
}

// MARK: - Find the subject

let threshold = 34.0
var minX = w, minY = h, maxX = 0, maxY = 0
var subjectPixels = 0
for y in stride(from: 0, to: h, by: 2) {
    for x in stride(from: 0, to: w, by: 2) {
        if backgroundDistance(x, y) > threshold {
            subjectPixels += 1
            if x < minX { minX = x }
            if x > maxX { maxX = x }
            if y < minY { minY = y }
            if y > maxY { maxY = y }
        }
    }
}
guard subjectPixels > 0, maxX > minX, maxY > minY else {
    FileHandle.standardError.write("no subject found\n".data(using: .utf8)!)
    exit(1)
}
let pad = w / 100
minX = max(0, minX - pad); minY = max(0, minY - pad)
maxX = min(w - 1, maxX + pad); maxY = min(h - 1, maxY + pad)
let bboxW = maxX - minX + 1
let bboxH = maxY - minY + 1
print("subject bbox \(bboxW)x\(bboxH) at (\(minX),\(minY))")

// Fitting the bounding box's diagonal would be needlessly timid: its corners are
// empty red, because the wing touches the left edge and the legs the bottom, but
// nothing occupies the corners. Fit the furthest actual subject pixel instead.
let canvasUnits = 108.0
let safeRadius = 33.0
let cx = Double(minX + maxX) / 2
let cy = Double(minY + maxY) / 2
var maxRadiusPx = 1.0
for y in stride(from: minY, through: maxY, by: 2) {
    for x in stride(from: minX, through: maxX, by: 2) {
        if backgroundDistance(x, y) > threshold {
            let dx = Double(x) - cx, dy = Double(y) - cy
            let r = (dx * dx + dy * dy).squareRoot()
            if r > maxRadiusPx { maxRadiusPx = r }
        }
    }
}
let fitScale = safeRadius / maxRadiusPx
let subjectUnitsW = Double(bboxW) * fitScale
let subjectUnitsH = Double(bboxH) * fitScale
print(String(format: "furthest subject pixel %.0fpx; artwork occupies %.1f x %.1f of 108",
             maxRadiusPx, subjectUnitsW, subjectUnitsH))

// MARK: - Build the cut-outs

/// The subject with the red field dissolved away.
func makeSubjectCutout(monochrome: Bool) -> CGImage? {
    var out = [UInt8](repeating: 0, count: bboxW * bboxH * 4)
    for y in 0..<bboxH {
        for x in 0..<bboxW {
            let sx = minX + x, sy = minY + y
            let d = backgroundDistance(sx, sy)
            // A ramp, not a hard cut, so antialiased wing edges stay smooth.
            // The floor sits just under the detection threshold on purpose: the
            // red field carries faint streaks and a soft vignette, and a lower
            // floor let those through at low alpha, printing a ghost of the crop
            // rectangle onto the background layer behind it.
            let a = max(0, min(1, (d - 30) / 45))
            let i = (y * bboxW + x) * 4
            let c = px(sx, sy)
            // Premultiplied, which is what this bitmap layout expects.
            if monochrome {
                out[i] = 0; out[i + 1] = 0; out[i + 2] = 0
            } else {
                out[i] = UInt8((c.r * a).rounded())
                out[i + 1] = UInt8((c.g * a).rounded())
                out[i + 2] = UInt8((c.b * a).rounded())
            }
            out[i + 3] = UInt8((a * 255).rounded())
        }
    }
    var image: CGImage?
    out.withUnsafeMutableBytes { buf in
        if let ctx = CGContext(
            data: buf.baseAddress, width: bboxW, height: bboxH,
            bitsPerComponent: 8, bytesPerRow: bboxW * 4, space: space,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) {
            image = ctx.makeImage()
        }
    }
    return image
}

guard let subjectColour = makeSubjectCutout(monochrome: false) else {
    FileHandle.standardError.write("cut-out failed\n".data(using: .utf8)!)
    exit(1)
}

// MARK: - Render

func makeContext(_ size: Int) -> CGContext? {
    CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
              bytesPerRow: 0, space: space,
              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
}

func drawBackground(_ ctx: CGContext, size: Int) {
    let topC = CGColor(red: fieldAtImageTop.0 / 255, green: fieldAtImageTop.1 / 255,
                       blue: fieldAtImageTop.2 / 255, alpha: 1)
    let bottomC = CGColor(red: fieldAtImageBottom.0 / 255, green: fieldAtImageBottom.1 / 255,
                          blue: fieldAtImageBottom.2 / 255, alpha: 1)
    guard let g = CGGradient(colorsSpace: space, colors: [topC, bottomC] as CFArray,
                             locations: [0, 1]) else { return }
    // CG y = size is the image's top, matching how the buffer was read.
    ctx.drawLinearGradient(g, start: CGPoint(x: 0, y: CGFloat(size)),
                           end: CGPoint(x: 0, y: 0), options: [])
}

func subjectRect(in size: Int) -> CGRect {
    let scale = Double(size) / canvasUnits
    let dw = subjectUnitsW * scale, dh = subjectUnitsH * scale
    return CGRect(x: (Double(size) - dw) / 2, y: (Double(size) - dh) / 2, width: dw, height: dh)
}

func write(_ image: CGImage, to path: String) {
    let url = URL(fileURLWithPath: path)
    try? FileManager.default.createDirectory(
        at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    guard let dest = CGImageDestinationCreateWithURL(
        url as CFURL, UTType.png.identifier as CFString, 1, nil) else { return }
    CGImageDestinationAddImage(dest, image, nil)
    CGImageDestinationFinalize(dest)
}

let densities: [(String, Int)] = [
    ("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432),
]

for (name, size) in densities {
    if let ctx = makeContext(size) {
        drawBackground(ctx, size: size)
        if let img = ctx.makeImage() {
            write(img, to: "\(resDir)/mipmap-\(name)/ic_launcher_background.png")
        }
    }
    if let ctx = makeContext(size) {
        ctx.interpolationQuality = .high
        ctx.draw(subjectColour, in: subjectRect(in: size))
        if let img = ctx.makeImage() {
            write(img, to: "\(resDir)/mipmap-\(name)/ic_launcher_foreground.png")
        }
    }
}

// Play Store listing icon: the original artwork, unmasked, at 512.
if let ctx = makeContext(512) {
    ctx.interpolationQuality = .high
    ctx.draw(sourceCG, in: CGRect(x: 0, y: 0, width: 512, height: 512))
    if let img = ctx.makeImage() {
        write(img, to: "\(resDir)/../../../../play-store-icon-512.png")
    }
}

// Flattened preview with the safe circle marked, so the fit can be checked by eye.
if let ctx = makeContext(432) {
    drawBackground(ctx, size: 432)
    ctx.interpolationQuality = .high
    ctx.draw(subjectColour, in: subjectRect(in: 432))
    ctx.setStrokeColor(CGColor(red: 1, green: 1, blue: 1, alpha: 0.8))
    ctx.setLineWidth(2)
    let r = 66.0 * (432.0 / canvasUnits) / 2
    ctx.strokeEllipse(in: CGRect(x: 216 - r, y: 216 - r, width: r * 2, height: r * 2))
    if let img = ctx.makeImage() {
        write(img, to: "\(resDir)/../../../../icon-preview.png")
    }
}

print("done")
