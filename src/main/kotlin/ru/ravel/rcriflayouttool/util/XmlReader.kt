package ru.ravel.rcriflayouttool.util

import java.io.File
import java.nio.charset.Charset

object XmlReader {

	fun readXmlSafe(bytes: ByteArray): String {
		return when {
			bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
				String(bytes, Charsets.UTF_16BE)

			bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
				String(bytes, Charsets.UTF_16LE)

			bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
				String(bytes, Charsets.UTF_8)

			else -> {
				val prolog = bytes.take(200).toByteArray().toString(Charsets.UTF_8)
				val encodingRegex = Regex("""encoding=["']([A-Za-z0-9_\-]+)["']""", RegexOption.IGNORE_CASE)
				val encoding = encodingRegex.find(prolog)?.groupValues?.get(1) ?: "UTF-8"
				String(bytes, Charset.forName(encoding))
			}
		}
	}


	fun readXmlSafe(file: File): String {
		return readXmlSafe(file.readBytes())
	}

}