package ru.ravel.rcriflayouttool.model.form

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class DisplayName(
	@JacksonXmlProperty(localName = "MnemonicId")
	val mnemonicId: String? = null
)