package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class MnemonicWrapper(
	@JacksonXmlProperty(localName = "MnemonicId")
	val mnemonicId: String? = null
)