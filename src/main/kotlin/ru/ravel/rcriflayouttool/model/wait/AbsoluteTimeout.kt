package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class AbsoluteTimeout(
	@JacksonXmlProperty(localName = "DataDocumentDateTime")
	val dataDocumentDateTime: DataDocumentDateTime? = null
)