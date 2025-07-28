package ru.ravel.rcriflayouttool.model.layout

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class EndPoints(
	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "DiagramEndPoint")
	val points: List<DiagramEndPoint> = emptyList()
)