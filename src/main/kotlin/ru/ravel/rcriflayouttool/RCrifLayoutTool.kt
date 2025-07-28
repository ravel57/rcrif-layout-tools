package ru.ravel.rcriflayouttool

import javafx.application.Application
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback
import java.io.File


class RCrifLayoutTool : Application() {

	override fun start(stage: Stage) {
		val procedures = FXCollections.observableArrayList<String>()
		val filtered = FilteredList(procedures) { true }
		val originalItems = FXCollections.observableArrayList(procedures)
		val filteredItems = FXCollections.observableArrayList(procedures)
		var selectedDirectory: File

		val folderField = TextField().apply {
			isEditable = false
			promptText = "Рабочая папка кредитного процесса"
		}

		val procedureNameField = ComboBox(filtered).apply {
			promptText = "Название процедуры"
			isEditable = true
			editor.textProperty().addListener { _, _, text ->
				filtered.setPredicate { item ->
					text.isNullOrBlank() || item.contains(text, ignoreCase = true)
				}
				if (!isShowing) show()
			}
		}

		val lastPath = loadSelectedPath()
		if (lastPath != null) {
			selectedDirectory = File(lastPath)
			folderField.text = lastPath
			val list = File(selectedDirectory, "Procedures").list()?.toList() ?: emptyList()
			procedures.setAll(list)
			procedureNameField.editor.clear()
		}
		val chooseFolderButton = Button("Выбрать папку").apply {
			setOnAction {
				val directoryChooser = DirectoryChooser().apply {
					title = "Выберите рабочую папку"
				}
				val dir = directoryChooser.showDialog(stage)
				if (dir != null) {
					selectedDirectory = dir
					val list = File(selectedDirectory, "Procedures").list()?.toList() ?: emptyList()
					procedures.setAll(list)
					procedureNameField.editor.clear()
					folderField.text = dir.absolutePath
					saveSelectedPath(dir.absolutePath)
				}
			}
		}

		val folderHBox = HBox(5.0, folderField, chooseFolderButton)

		val fieldCol = TableColumn<ParamRow, String>("Поле").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val valueCol = TableColumn<ParamRow, String>("Значение").apply {
			cellValueFactory = Callback { it.value.value }
			isEditable = true
		}
		val tableView = TableView<ParamRow>().apply {
			columns.addAll(fieldCol, valueCol)
			isEditable = true
		}
		tableView.items.addAll(
			ParamRow(SimpleStringProperty("Параметр 1"), SimpleStringProperty("Значение 1")),
			ParamRow(SimpleStringProperty("Параметр 2"), SimpleStringProperty("Значение 2")),
		)

		val rootVBox = VBox(
			10.0,
			Label("Рабочая папка кредитного процесса:"), folderHBox,
			Label("Название процедуры:"), procedureNameField,
			Label("Параметры:"), tableView,
		).apply {
			padding = Insets(20.0)
		}
		stage.scene = Scene(rootVBox, 600.0, 500.0)
		stage.title = "rCrif Layout Tool"
		stage.show()
	}


	private fun getConfigFile(): File {
		val os = System.getProperty("os.name").lowercase()
		val configDir = when {
			os.contains("win") -> File(System.getenv("APPDATA"), "rcrif-layout-tool")
			os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/rcrif-layout-tool")
			else -> File(System.getProperty("user.home"), ".config/rcrif-layout-tool")
		}
		if (!configDir.exists()) {
			configDir.mkdirs()
		}
		return File(configDir, "config.properties")
	}


	private fun saveSelectedPath(path: String) {
		val props = java.util.Properties()
		props["selectedDirectory"] = path
		getConfigFile().outputStream().use { props.store(it, null) }
	}


	private fun loadSelectedPath(): String? {
		val file = getConfigFile()
		if (!file.exists()) return null
		val props = java.util.Properties()
		file.inputStream().use { props.load(it) }
		return props.getProperty("selectedDirectory")
	}


	data class ParamRow(val field: SimpleStringProperty, val value: SimpleStringProperty)
}


fun main() {
	Application.launch(RCrifLayoutTool::class.java)
}