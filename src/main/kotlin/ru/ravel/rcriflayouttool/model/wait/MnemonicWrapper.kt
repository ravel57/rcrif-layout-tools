package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class MnemonicWrapper @JsonCreator constructor(
	@JsonProperty("MnemonicId")
	@JacksonXmlProperty(localName = "MnemonicId")
	val mnemonicId: String? = null
)