package tr.com.marssolar.web

import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

object Images {
    const val MAX_EDGE = 1600
    const val MAX_BYTES = 900_000

    fun jpeg(bytes: ByteArray): ByteArray {
        val source = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Fotoğraf açılamadı. JPG veya PNG yükleyin.")
        val scale = minOf(1.0, MAX_EDGE.toDouble() / max(source.width, source.height))
        val width = max(1, (source.width * scale).roundToInt())
        val height = max(1, (source.height * scale).roundToInt())
        val canvas = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = canvas.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, width, height)
        graphics.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null)
        graphics.dispose()
        var quality = 0.86f
        while (true) {
            val encoded = encode(canvas, quality)
            if (encoded.size <= MAX_BYTES || quality <= 0.5f) {
                if (encoded.size > MAX_BYTES) throw IllegalArgumentException("Fotoğraf çok büyük.")
                return encoded
            }
            quality -= 0.08f
        }
    }

    private fun encode(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        writer.output = ImageIO.createImageOutputStream(output)
        val params = writer.defaultWriteParam
        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
        params.compressionQuality = quality
        writer.write(null, IIOImage(image, null, null), params)
        writer.dispose()
        return output.toByteArray()
    }
}
