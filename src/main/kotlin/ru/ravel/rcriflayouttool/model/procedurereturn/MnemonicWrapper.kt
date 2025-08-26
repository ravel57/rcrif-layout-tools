package ru.ravel.rcriflayouttool.model.procedurereturn

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class MnemonicWrapper(
	@JacksonXmlProperty(localName = "MnemonicId")
	val mnemonicId: String? = null
)