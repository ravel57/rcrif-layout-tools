package ru.ravel.rcriflayouttool

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import javafx.application.Application
import javafx.application.Platform
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
import ru.ravel.rcriflayouttool.model.ParamRow
import ru.ravel.rcriflayouttool.model.layout.DiagramLayout
import ru.ravel.rcriflayouttool.model.properties.ProcedureCallActivityDefinition
import java.io.File


class RCrifLayoutTool : Application() {

	private var selectedDirectory: File? = null


	override fun start(stage: Stage) {
		val allProcedures = FXCollections.observableArrayList<String>()
		val filteredProcedures = FilteredList(allProcedures) { true }

		val folderField = TextField().apply {
			isEditable = false
			promptText = "Рабочая папка кредитного процесса"
		}


		val fieldCol = TableColumn<ParamRow, String>("Название в процессе").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val tableView = TableView<ParamRow>().apply {
			columns.addAll(fieldCol)
			isEditable = true
		}
		fieldCol.prefWidthProperty().bind(tableView.widthProperty().subtract(2))

		val procedureBox = ComboBox(filteredProcedures).apply {
			isEditable = true
			promptText = "Название процедуры"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredProcedures.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredProcedures.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredProcedures.setPredicate { true }
						editor.text = newVal
						hide()
						tableView.items.clear()
						tableView.items.addAll(exploreLayouts(newVal).map { ParamRow(SimpleStringProperty(it)) })
					}
				}
			}
		}

		val lastPath = loadSelectedPath()
		if (lastPath != null) {
			selectedDirectory = File(lastPath)
			if (selectedDirectory?.exists() == true) {
				folderField.text = lastPath
				val list = File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList()
				allProcedures.setAll(list)
			}
		}
		val chooseFolderButton = Button("Выбрать папку").apply {
			setOnAction {
				selectedDirectory = DirectoryChooser().apply {
					title = "Выберите рабочую папку"
				}.showDialog(stage)
				if (selectedDirectory != null) {
					folderField.text = selectedDirectory!!.absolutePath
					saveSelectedPath(selectedDirectory!!.absolutePath)
					allProcedures.setAll(File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList())
					procedureBox.selectionModel.clearSelection()
					procedureBox.value = null
					procedureBox.editor.clear()
					filteredProcedures.setPredicate { true }
				}
			}
		}

		val root = VBox(
			10.0,
			Label("Рабочая папка кредитного процесса:"), HBox(5.0, folderField, chooseFolderButton),
			Label("Название процедуры:"), procedureBox,
			Label("Параметры:"), tableView
		).apply {
			padding = Insets(20.0)
		}

		stage.scene = Scene(root, 600.0, 500.0)
		stage.title = "rCrif Layout Tool"
		stage.show()
	}


	private fun exploreLayouts(selectedProcedure: String): List<String?> {
		val mapper = XmlMapper()

		val pcNamesFromProcedures = File(selectedDirectory, "Procedures")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, ProcedureCallActivityDefinition::class.java) }
			}
			.filter { it.procedureToCall == selectedProcedure }
			.map { it.referenceName }

		val proceduresLayouts = File(selectedDirectory, "Procedures")
			.listFiles { file -> File(file, "Layout.xml").exists() }
			?.map { file -> File(file, "Layout.xml") }
			?.mapNotNull { file ->
				file.inputStream().use { input -> mapper.readValue(input, DiagramLayout::class.java) }
			}
			?.flatMap { layout ->
				layout.elements?.diagramElements
					?.filter { de -> pcNamesFromProcedures.contains(de.reference) }
					?.map { de -> de.uid }
					?: emptyList()
			}

		val mainLayouts = File(selectedDirectory, "MainFlow")
			.listFiles { file -> file.isDirectory }
			?.mapNotNull { dir ->
				val props = File(dir, "Properties.xml")
				if (props.isFile) {
					props.inputStream().use { input ->
						mapper.readValue(input, ProcedureCallActivityDefinition::class.java)
					}
				} else null
			}
			?.filter { it.procedureToCall == selectedProcedure }
			?.map { it.referenceName }
			?: emptyList()

		return mainLayouts + pcNamesFromProcedures
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

}


fun main() {
	Application.launch(RCrifLayoutTool::class.java)
}