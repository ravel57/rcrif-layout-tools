package ru.ravel.rcriflayouttool.model.layout

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class Comment(
	@JacksonXmlProperty(localName = "X")
	val x: Int = 0,
	@JacksonXmlProperty(localName = "Y")
	val y: Int = 0,
	@JacksonXmlProperty(localName = "Width")
	val width: Int = 0,
	@JacksonXmlProperty(localName = "Height")
	val height: Int = 0,
	@JacksonXmlProperty(localName = "Text")
	val text: String? = null
)