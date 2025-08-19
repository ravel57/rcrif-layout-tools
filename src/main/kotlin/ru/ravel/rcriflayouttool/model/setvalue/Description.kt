package ru.ravel.rcriflayouttool.model.setvalue

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class Description(
	@JacksonXmlProperty(localName = "MnemonicId")
	val mnemonicId: String? = null
)