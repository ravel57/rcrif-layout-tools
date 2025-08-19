package ru.ravel.rcriflayouttool.model.dispatch

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Description(
	@JacksonXmlProperty(localName = "MnemonicId")
	val mnemonicId: String? = null
)