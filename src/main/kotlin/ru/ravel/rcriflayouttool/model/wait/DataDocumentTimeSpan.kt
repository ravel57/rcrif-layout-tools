package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class DataDocumentTimeSpan(
	@JacksonXmlProperty(localName = "MinutesField")
	val minutesField: MinutesField? = null
)

