package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class AbsoluteTimeout(
	@JacksonXmlProperty(localName = "DataDocumentDateTime")
	val dataDocumentDateTime: DataDocumentDateTime? = null
)