package ru.ravel.rcriflayouttool.model.layout

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class DiagramElement(
	@JacksonXmlProperty(isAttribute = true, localName = "UID")
	val uid: String? = null,

	@JacksonXmlProperty(localName = "X")
	val x: Int = 0,
	@JacksonXmlProperty(localName = "Y")
	val y: Int = 0,
	@JacksonXmlProperty(localName = "Width")
	val width: Int = 0,
	@JacksonXmlProperty(localName = "Height")
	val height: Int = 0,
	@JacksonXmlProperty(localName = "Reference")
	val reference: String? = null,

	@JacksonXmlProperty(localName = "Comment")
	val comment: Comment? = null,

	@JacksonXmlProperty(localName = "OutConnectionRefs")
	val outConnectionRefs: RefList? = null,

	@JacksonXmlProperty(localName = "InConnectionRefs")
	val inConnectionRefs: RefList? = null
)