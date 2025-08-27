package ru.ravel.rcriflayouttool

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import javafx.animation.PauseTransition
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.util.Callback
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import org.fxmisc.richtext.model.StyleSpansBuilder
import org.fxmisc.richtext.model.TwoDimensional.Bias
import org.reactfx.Subscription
import ru.ravel.rcriflayouttool.dto.*
import ru.ravel.rcriflayouttool.model.bizrule.BizRule
import ru.ravel.rcriflayouttool.model.datamapping.DataMapping
import ru.ravel.rcriflayouttool.model.datasource.DataSource
import ru.ravel.rcriflayouttool.model.dispatch.Dispatch
import ru.ravel.rcriflayouttool.model.form.Form
import ru.ravel.rcriflayouttool.model.layout.*
import ru.ravel.rcriflayouttool.model.procedure.ProcedureCall
import ru.ravel.rcriflayouttool.model.segmentationtree.BusinessRule
import ru.ravel.rcriflayouttool.model.segmentationtree.SegmentationTree
import ru.ravel.rcriflayouttool.model.sendemail.SendEmail
import ru.ravel.rcriflayouttool.model.setphase.SetPhase
import ru.ravel.rcriflayouttool.model.setvalue.SetValueActivity
import ru.ravel.rcriflayouttool.model.wait.Wait
import ru.ravel.rcriflayouttool.util.GitUnit
import ru.ravel.rcriflayouttool.util.XmlReader
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.time.Duration
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import javafx.util.Duration as FxDuration

class RCrifLayoutTool : Application() {

	private var selectedDirectory: File? = null
	private var layoutActivitiesCache: List<ActivitiesForMenu> = emptyList()
	private var subscription: Subscription? = null
	private val suspendSync = SimpleBooleanProperty(false)
	private val isUpdating = AtomicBoolean(false)
	private var currentDiffTask: Task<Triple<List<String>, List<String>, DiffResult>>? = null

	private val diffNav = mutableListOf<DiffNav>()
	private var diffIdx = -1


	override fun start(stage: Stage) {
		val allProcedures = FXCollections.observableArrayList<String>()
		val filteredProcedures = FilteredList(allProcedures) { true }

		val allConnectors = FXCollections.observableArrayList<String>()
		val filteredConnectors = FilteredList(allConnectors) { true }

		val allMarge = FXCollections.observableArrayList<String>()
		val filteredMarge = FilteredList(allMarge) { true }

		val filteredAddingNewSplit = FilteredList(allMarge) { true }
		val allFormNewSplit = FXCollections.observableArrayList<String>()
		val filteredFormNewSplit = FilteredList(allFormNewSplit) { true }
		val allToNewSplit = FXCollections.observableArrayList<String>()
		val filteredToNewSplit = FilteredList(allToNewSplit) { true }
		val allFormExitsNewSplit = FXCollections.observableArrayList<String>()
		val filteredFormExitsNewSplit = FilteredList(allFormExitsNewSplit) { true }
		val allToNewExitsSplit = FXCollections.observableArrayList<String>()
		val filteredToExitsNewSplit = FilteredList(allToNewExitsSplit) { true }

		val allExitTypes = FXCollections.observableArrayList(
			ActivityType.values()
				.filter { it !in arrayOf(ActivityType.UNKNOWN, ActivityType.END_PROCEDURE, ActivityType.PROCEDURE_RETURN) }
				.map { it.name }
		)

		val newItem = MenuItem("Отчет").apply {
			setOnAction {
				reportToExcel()
			}
		}
		val fileMenu = Menu("Файл").apply {
			items.addAll(newItem)
		}
		val menuBar = MenuBar().apply {
			menus.addAll(fileMenu)
		}

		val folderField = TextField().apply {
			isEditable = false
			promptText = "Рабочая папка кредитного процесса"
		}

		/* Поиск процедур */
		val searchProcedureTableColumn = TableColumn<ParamRow, String>("Название в процессе").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val procedureTableView = TableView<ParamRow>().apply {
			columns.setAll(searchProcedureTableColumn)
			isEditable = true
			setRowFactory { _ ->
				val row = TableRow<ParamRow>()
				row.setOnContextMenuRequested { event ->
					if (layoutActivitiesCache.isEmpty()) return@setOnContextMenuRequested
					val menu = ContextMenu()
					val items = layoutActivitiesCache
						.filter { la -> la.reference == row.item.field.value }
						.flatMap { a ->
							List(a.toActivity.size) { index -> "${a.exitName[index]} <-> ${a.toActivity[index]}" }
						}
					menu.items.setAll(items.distinct().map { MenuItem(it) })
					menu.show(row, event.screenX, event.screenY)
				}
				row
			}
		}
		searchProcedureTableColumn.prefWidthProperty().bind(procedureTableView.widthProperty().subtract(2))

		val procedureComboBox = ComboBox(filteredProcedures).apply {
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
						val activities = getProceduresActivities(newVal)
						layoutActivitiesCache = activities
						procedureTableView.items.setAll(activities.map { ParamRow(SimpleStringProperty(it.reference)) })
					}
				}
			}
		}

		val lastPath = loadSelectedPath()
		if (lastPath != null) {
			selectedDirectory = File(lastPath)
			if (selectedDirectory?.exists() == true) {
				folderField.text = lastPath
				allProcedures.setAll(File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList())
				allConnectors.setAll(getAllConnectors(lastPath).map { it.connectorName }.distinct())
				allMarge.setAll(
					File(selectedDirectory, "Procedures").list()
						?.sorted()
						?.toMutableList()
						?.apply { add(0, "MainFlow") }
						?: emptyList())
			}
		}
		val chooseFolderButton = Button("Выбрать папку").apply {
			setOnAction {
				val initialDir = lastPath?.let { File(it).parentFile }
				val chooser = DirectoryChooser().apply {
					title = "Выберите рабочую папку"
					if (initialDir?.exists() == true) {
						initialDirectory = initialDir
					}
				}
				selectedDirectory = chooser.showDialog(stage)
				if (selectedDirectory != null) {
					folderField.text = selectedDirectory!!.absolutePath
					saveSelectedPath(selectedDirectory!!.absolutePath)
					allProcedures.setAll(File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList())
					allConnectors.setAll(getAllConnectors(selectedDirectory!!.absolutePath).map { it.connectorName }.distinct())
					allMarge.setAll(
						File(selectedDirectory, "Procedures").list()
							?.sorted()
							?.toMutableList()
							?.apply { add(0, "MainFlow") }
							?: emptyList())
					procedureComboBox.selectionModel.clearSelection()
					procedureComboBox.value = null
					procedureComboBox.editor.clear()
					filteredProcedures.setPredicate { true }
				}
			}
		}

		val folderChooserPanel = HBox(5.0, folderField, chooseFolderButton).apply {
			HBox.setHgrow(folderField, Priority.ALWAYS)
		}

		val proceduresTabContent = VBox(
			10.0, HBox(Label("Название процедуры:"), procedureComboBox).apply { padding = Insets(5.0) }, procedureTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск коннекторов */
		val connectorTableColumn = TableColumn<ParamRow, String>("Название в активности").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val connectorTableView = TableView<ParamRow>().apply {
			columns.setAll(connectorTableColumn)
			isEditable = true
		}
		connectorTableColumn.prefWidthProperty().bind(connectorTableView.widthProperty().subtract(2.0))

		val connectorsComboBox = ComboBox(filteredConnectors).apply {
			isEditable = true
			promptText = "Название коннектора"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredConnectors.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredConnectors.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredConnectors.setPredicate { true }
						editor.text = newVal
						hide()
						connectorTableView.items.setAll(getConnectorReferences(folderField.text, newVal).map {
							ParamRow(SimpleStringProperty(it))
						})
					}
				}
			}
		}
		val connectorsTabContent = VBox(
			10.0, HBox(Label("Название коннектора:"), connectorsComboBox).apply { padding = Insets(5.0) }, connectorTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Marge */
		val margeArea1 = CodeArea()
		val margeArea2 = CodeArea()

		val mergeComboBox = ComboBox(filteredMarge).apply {
			isEditable = true
			promptText = "Layout процедуры"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredMarge.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredMarge.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredMarge.setPredicate { true }
						editor.text = newVal
						hide()
						val file = if (newVal == "MainFlow") {
							File(selectedDirectory, "${newVal}/Layout.xml")
						} else {
							File(selectedDirectory, "Procedures/${newVal}/Layout.xml")
						}
						val text = file.let { f ->
							XmlReader.readXmlSafe(f.takeIf { it.exists() })
						}
						margeArea2.replaceText(text)
						val previousFileVersion = GitUnit.getPreviousFileVersion(selectedDirectory!!, file.absolutePath)
						margeArea1.replaceText(previousFileVersion)
					}
				}
			}
		}
		val prevBtn = Button("◀").apply {
			isDisable = true
		}
		val nextBtn = Button("▶").apply {
			isDisable = true
		}

		val openSearchBtn = Button("🔍 Поиск").apply {
			setOnAction { showSearchWindow(margeArea1, margeArea2) }
		}

		prevBtn.setOnAction {
			gotoDiff(-1, margeArea1, margeArea2)
			updateNavButtons(prevBtn, nextBtn)
		}
		nextBtn.setOnAction {
			gotoDiff(+1, margeArea1, margeArea2)
			updateNavButtons(prevBtn, nextBtn)
		}

		val scroll1 = VirtualizedScrollPane(margeArea1)
		val scroll2 = VirtualizedScrollPane(margeArea2)
		val wrapped1 = wrapWithGrayFiller(scroll1).apply { maxHeight = Double.MAX_VALUE }
		val wrapped2 = wrapWithGrayFiller(scroll2).apply { maxHeight = Double.MAX_VALUE }


		val textLoadIndicator = ProgressIndicator().apply {
			isVisible = false
			maxWidth = 20.0
			maxHeight = 20.0
		}

		bindScrollSync(scroll1, scroll2)
		subscribeToChanges(margeArea1, margeArea2, prevBtn, nextBtn, textLoadIndicator)

		val panesHBox = HBox(
			4.0,
			Label("Layout: "),
			mergeComboBox,
			prevBtn,
			nextBtn,
			textLoadIndicator,
			openSearchBtn,
		).apply {
			spacing = 4.0
			isFillHeight = true
			HBox.setHgrow(wrapped1, Priority.ALWAYS)
			HBox.setHgrow(wrapped2, Priority.ALWAYS)
		}

		val contentBox = HBox(4.0, wrapped1, wrapped2).apply {
			spacing = 4.0
			isFillHeight = true
			HBox.setHgrow(wrapped1, Priority.ALWAYS)
			HBox.setHgrow(wrapped2, Priority.ALWAYS)
			wrapped1.maxHeight = Double.MAX_VALUE
			wrapped2.maxHeight = Double.MAX_VALUE
		}

		val margeBp = BorderPane().apply {
			top = panesHBox
			center = contentBox
			padding = Insets(20.0)
		}
		VBox.setVgrow(margeBp, Priority.ALWAYS)

		/* Неиспользуемые процедуры */
		val unusedProcedureTableColumn = TableColumn<ParamRow, String>("Название процедуры").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val unusedProcedureTableView = TableView<ParamRow>().apply {
			columns.setAll(unusedProcedureTableColumn)
			isEditable = true
		}
		unusedProcedureTableColumn.prefWidthProperty().bind(unusedProcedureTableView.widthProperty().subtract(2))
		val unusedProceduresProgress = ProgressBar().apply {
			isVisible = false
			maxWidth = 100.0
		}
		val unusedProceduresButton = Button("Поиск").apply {
			isDisable = selectedDirectory?.exists() == false
			setOnAction {
				isDisable = true
				unusedProceduresProgress.isVisible = true
				val task = object : Task<List<ParamRow>>() {
					override fun call(): List<ParamRow> {
						val dir = selectedDirectory ?: return emptyList()
						val files = File(dir, "Procedures").list() ?: arrayOf()
						val result = mutableListOf<ParamRow>()
						for ((i, name) in files.withIndex()) {
							updateProgress(i.toLong(), files.size.toLong())
							val acts = getProceduresActivities(name)
							if (acts.isEmpty()) {
								result.add(ParamRow(SimpleStringProperty(name)))
							}
						}
						return result
					}
				}
				unusedProceduresProgress.progressProperty().bind(task.progressProperty())
				task.setOnSucceeded { _ ->
					unusedProcedureTableView.items.setAll(task.value)
					unusedProceduresProgress.isVisible = false
					isDisable = false
				}
				task.setOnFailed {
					unusedProceduresProgress.isVisible = false
					isDisable = false
				}
				Executors.newSingleThreadExecutor().submit(task)
			}
		}
		val unusedProcedureTabContent = VBox(
			10.0, HBox(5.0, unusedProceduresButton, unusedProceduresProgress), unusedProcedureTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск атрибутов  */
		val attributeTableColumn = TableColumn<ParamRow, String>("Название активности").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val attributeTableView = TableView<ParamRow>().apply {
			columns.setAll(attributeTableColumn)
			isEditable = true
		}
		val attributeTextField = TextField().apply {
			textProperty().addListener { _, _, newValue ->
				val attributes = searchAttribute(newValue)
				attributeTableView.items.setAll(attributes.map { ParamRow(SimpleStringProperty(it)) })
			}
		}
		attributeTableColumn.prefWidthProperty().bind(attributeTableView.widthProperty().subtract(2.0))
		val attributeBox = VBox(
			10.0,
			Label("Поиск во всех *.xslt и SV файлах"),
			HBox(Label("Название атрибута: "), attributeTextField),
			attributeTableView,
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск затираний */
		val erasuresFirstColumn = TableColumn<DualParamRow, String>("Активность").apply {
			cellValueFactory = Callback { cellData ->
				cellData.value.firstField
			}
		}
		val erasuresSecondColumn = TableColumn<DualParamRow, String>("DataDock").apply {
			cellValueFactory = Callback { cellData ->
				cellData.value.secondField
			}
		}
		val erasuresTableView = TableView<DualParamRow>().apply {
			columns.setAll(erasuresFirstColumn, erasuresSecondColumn)
			isEditable = true
		}
		val erasuresButton = Button("Поиск").apply {
			setOnAction {
				val flatMap = searchErasures().flatMap { p ->
					p.second.map {
						DualParamRow(SimpleStringProperty(p.first), SimpleStringProperty(it))
					}
				}
				erasuresTableView.items.setAll(flatMap)
			}
		}
		val erasuresBox = VBox(
			10.0, erasuresButton, erasuresTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск неиспользуемых FO */
		val unusedFoColumn = TableColumn<ParamRow, String>("Название FO").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val unusedFoTableView = TableView<ParamRow>().apply {
			columns.setAll(unusedFoColumn)
			isEditable = true
		}
		unusedFoColumn.prefWidthProperty().bind(unusedProcedureTableView.widthProperty().subtract(2))
		val unusedFoButton = Button("Поиск").apply {
			setOnAction {
				unusedFoTableView.items.setAll(searchUnusedFo().map { ParamRow(SimpleStringProperty(it)) })
			}
		}
		val unusedFoBox = VBox(
			10.0, unusedFoButton, unusedFoTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск неиспользуемых BR (ST + FM + DR) */
		val unusedBrColumn = TableColumn<ParamRow, String>("Название BusinessRule (ST + FM + DR)").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val unusedBrTableView = TableView<ParamRow>().apply {
			columns.setAll(unusedBrColumn)
			isEditable = true
		}
		unusedBrColumn.prefWidthProperty().bind(unusedProcedureTableView.widthProperty().subtract(2))
		val unusedBrButton = Button("Поиск").apply {
			setOnAction {
				unusedBrTableView.items.setAll(searchUnusedBr().mapNotNull { ParamRow(SimpleStringProperty(it)) })
			}
		}
		val unusedBrBox = VBox(
			10.0, unusedBrButton, unusedBrTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск неиспользуемых активностей */
		val unusedActivitiesFirstColumn = TableColumn<TripleParamRow, String>("Активность").apply {
			cellValueFactory = Callback { cellData ->
				cellData.value.firstField
			}
		}
		val unusedActivitiesSecondColumn = TableColumn<TripleParamRow, String>("Количество входов").apply {
			cellValueFactory = Callback { cellData ->
				cellData.value.secondField
			}
		}
		val unusedActivitiesThirdColumn = TableColumn<TripleParamRow, String>("Количество выходов").apply {
			cellValueFactory = Callback { cellData ->
				cellData.value.thirdField
			}
		}
		val unusedActivitiesTableView = TableView<TripleParamRow>().apply {
			columns.setAll(unusedActivitiesFirstColumn, unusedActivitiesSecondColumn, unusedActivitiesThirdColumn)
			isEditable = true
		}
		val unusedActivitiesButton = Button("Поиск").apply {
			setOnAction {
				val flatMap = searchUnusedActivities().map {
					TripleParamRow(
						SimpleStringProperty(it.activity),
						SimpleStringProperty(it.inCount.toString()),
						SimpleStringProperty(it.outCount.toString())
					)
				}
				unusedActivitiesTableView.items.setAll(flatMap)
			}
		}
		val unusedActivitiesBox = VBox(
			10.0, unusedActivitiesButton, unusedActivitiesTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Нелатинские названия */
		val invalidCharactersFirstColumn = TableColumn<DualParamRow, String>("Активность").apply {
			cellValueFactory = Callback { it.value.firstField }
			isEditable = true
		}
		val invalidCharactersSecondColumn = TableColumn<DualParamRow, String>("Строка").apply {
			cellValueFactory = Callback { it.value.secondField }
			isEditable = true
		}
		val invalidCharactersTableView = TableView<DualParamRow>().apply {
			columns.setAll(invalidCharactersFirstColumn, invalidCharactersSecondColumn)
			isEditable = true
		}
		unusedBrColumn.prefWidthProperty().bind(unusedProcedureTableView.widthProperty().subtract(2))
		val invalidCharactersButton = Button("Поиск").apply {
			setOnAction {
				invalidCharactersTableView.items.setAll(searchInvalidCharacters().map {
					DualParamRow(SimpleStringProperty(it.activity), SimpleStringProperty(it.value))
				})
			}
		}
		val invalidCharactersBox = VBox(
			10.0, invalidCharactersButton, invalidCharactersTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Неправильные названия */
		val invalidNamingFirstColumn = TableColumn<TripleParamRow, String>("Процедура").apply {
			cellValueFactory = Callback { it.value.firstField }
			isEditable = true
		}
		val invalidNamingSecondColumn = TableColumn<TripleParamRow, String>("Неправильное название").apply {
			cellValueFactory = Callback { it.value.secondField }
			isEditable = true
		}
		val invalidNamingThirdColumn = TableColumn<TripleParamRow, String>("Тип").apply {
			cellValueFactory = Callback { it.value.thirdField }
			isEditable = true
		}
		val invalidNamingTableView = TableView<TripleParamRow>().apply {
			columns.setAll(invalidNamingFirstColumn, invalidNamingSecondColumn, invalidNamingThirdColumn)
			isEditable = true
		}
		val invalidNamingButton = Button("Поиск").apply {
			setOnAction {
				invalidNamingTableView.items.setAll(searchInvalidNaming().map {
					TripleParamRow(
						SimpleStringProperty(it.first),
						SimpleStringProperty(it.second),
						SimpleStringProperty(it.third)
					)
				})
			}
		}
		val invalidNamingBox = VBox(
			10.0, invalidNamingButton, invalidNamingTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Пустые выходы */
		val emptyExitsFirstColumn = TableColumn<DualParamRow, String>("Активность").apply {
			cellValueFactory = Callback { it.value.firstField }
			isEditable = true
		}
		val emptyExitsSecondColumn = TableColumn<DualParamRow, String>("Выход").apply {
			cellValueFactory = Callback { it.value.secondField }
			isEditable = true
		}
		val emptyExitsTableView = TableView<DualParamRow>().apply {
			columns.setAll(emptyExitsFirstColumn, emptyExitsSecondColumn)
			isEditable = true
		}
		unusedBrColumn.prefWidthProperty().bind(unusedProcedureTableView.widthProperty().subtract(2))
		val emptyExitsComboBox = ComboBox(allExitTypes).apply {
			isEditable = false
		}
		val ignoreFailedCheckbox = CheckBox("Игнорировать Failed выходы")
		val emptyExitsButton = Button("Поиск").apply {
			setOnAction {
				emptyExitsTableView.items.setAll(searchEmptyExits(emptyExitsComboBox.value, ignoreFailedCheckbox.isSelected).map {
					DualParamRow(SimpleStringProperty(it.first), SimpleStringProperty(it.second))
				})
			}
		}
		val emptyExitsBox = VBox(
			10.0, HBox(5.0, emptyExitsComboBox, ignoreFailedCheckbox, emptyExitsButton), emptyExitsTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Добавление связей */
		val addingNewSplitComboBox = ComboBox(filteredAddingNewSplit).apply {
			isEditable = true
			promptText = "Layout"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredAddingNewSplit.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredAddingNewSplit.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredAddingNewSplit.setPredicate { true }
						editor.text = newVal
						hide()

						val dir = if (newVal == "MainFlow") {
							File(selectedDirectory, "MainFlow")
						} else {
							File(selectedDirectory, "Procedures/$newVal")
						}

						if (dir.exists() && dir.isDirectory) {
							val subFolders = dir.listFiles()
								?.filter { it.isDirectory }
								?.map { it.name }
								?.sorted()
								?: emptyList()

							allFormNewSplit.setAll(subFolders)
							allToNewSplit.setAll(subFolders)
						} else {
							allFormNewSplit.clear()
							allToNewSplit.clear()
						}
					}
				}
			}

			setOnHidden { commitComboValue(this, allFormNewSplit) }
			editor.setOnAction { commitComboValue(this, allFormNewSplit) }
			focusedProperty().addListener { _, _, hasFocus -> if (!hasFocus) commitComboValue(this, allFormNewSplit) }
		}
		val fromNewSplitComboBox = ComboBox(filteredFormNewSplit).apply {
			isEditable = true
			promptText = "Активность"
			maxWidth = 200.0
			var guard = false

			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredFormNewSplit.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredFormNewSplit.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}

			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredFormNewSplit.setPredicate { true }
						editor.text = newVal
						hide()
					}
				}
			}
		}
		val fromNewSplitComboBoxExits = TextField().apply {//ComboBox(filteredFormExitsNewSplit).apply {
			isEditable = true
			promptText = "Выход"
			maxWidth = 200.0
		}
		val toNewSplitComboBox = ComboBox(filteredToNewSplit).apply {
			isEditable = true
			promptText = "Активность"
			maxWidth = 200.0
			var guard = false

			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredToNewSplit.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredToNewSplit.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}

			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredToNewSplit.setPredicate { true }
						editor.text = newVal
						hide()
					}
				}
			}
		}
		val toNewSplitComboBoxExits = TextField().apply {// ComboBox(filteredToExitsNewSplit).apply {
			isEditable = true
			promptText = "Вход"
			maxWidth = 200.0
			text = "Enter"
		}
		val addNewSplitButton = Button().apply {
			text = "Добавить связь"

			setOnAction {
				addingNewSplit(
					addingNewSplitComboBox.editor.text ?: "",
					fromNewSplitComboBox.editor.text ?: "",
					fromNewSplitComboBoxExits.text ?: "",
					toNewSplitComboBox.editor.text ?: "",
					toNewSplitComboBoxExits.text ?: ""
				)
			}
		}
		val addingNewSplitHBox = VBox(
			4.0,
			addingNewSplitComboBox,
			HBox(Label("Из"), fromNewSplitComboBox, fromNewSplitComboBoxExits),
			HBox(Label("В"), toNewSplitComboBox, toNewSplitComboBoxExits),
			addNewSplitButton,
		).apply {
			padding = Insets(20.0)
		}

		/* Сборка вкладок */
		val tabPane = TabPane(
			Tab("Поиск использования процедур", proceduresTabContent).apply { isClosable = false },
			Tab("Поиск использования коннекторов", connectorsTabContent).apply { isClosable = false },
			Tab("Поиск атрибутов", attributeBox).apply { isClosable = false },
			Tab("Layout marge", margeBp).apply { isClosable = false },
			Tab("Неиспользуемые FO", unusedFoBox).apply { isClosable = false },
			Tab("Неиспользуемые BR в ST+FM+DR", unusedBrBox).apply { isClosable = false },
			Tab("Неиспользуемые процедуры", unusedProcedureTabContent).apply { isClosable = false },
			Tab("Поиск затираний", erasuresBox).apply { isClosable = false },
			Tab("Неиспользуемые активности", unusedActivitiesBox).apply { isClosable = false },
			Tab("Нелатинские названия", invalidCharactersBox).apply { isClosable = false },
			Tab("Неправильные названия", invalidNamingBox).apply { isClosable = false },
			Tab("Пустые выходы", emptyExitsBox).apply { isClosable = false },
			Tab("Добавление связей", addingNewSplitHBox).apply { isClosable = false },
		)

		VBox.setVgrow(tabPane, Priority.ALWAYS)

		val root = VBox(
			10.0, menuBar, folderChooserPanel, tabPane
		)

		stage.scene = Scene(root, 700.0, 500.0).apply {
			javaClass.classLoader.getResource("diff.css")?.let {
				stylesheets.add(it.toExternalForm())
			}
		}
		stage.title = "rCrif Layout Tool"
		stage.show()
		Platform.runLater {
			procedureComboBox.requestFocus()
			procedureComboBox.editor.requestFocus()
			recalculateDiff(margeArea1, margeArea2, prevBtn, nextBtn)
		}
	}


	private fun getProceduresActivities(selectedProcedure: String): List<ActivitiesForMenu> {
		val mapper = XmlMapper()

		val pcNamesFromMainFlow = File(selectedDirectory, "MainFlow")
			.listFiles { file -> file.isDirectory }
			?.mapNotNull { dir ->
				val props = File(dir, "Properties.xml")
				if (props.isFile) {
					props.inputStream().use { input ->
						mapper.readValue(input, ProcedureCall::class.java)
					}
				} else null
			}
			?.filter { it.procedureToCall == selectedProcedure }
			?: emptyList()

		val pcNamesFromProcedures = File(selectedDirectory, "Procedures")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, ProcedureCall::class.java) }
			}
			.filter { it.procedureToCall == selectedProcedure }

		val diagramLayout = File(selectedDirectory, "Procedures")
			.listFiles { file -> File(file, "Layout.xml").exists() }
			?.map { file -> File(file, "Layout.xml") }
			?.mapNotNull { file ->
				file.inputStream().use { input -> mapper.readValue(input, DiagramLayout::class.java) }
			}
			?.toMutableList()
		val diagramLayoutMainFlow = File(selectedDirectory, "MainFlow/Layout.xml").let { file ->
			file.takeIf { it.exists() }?.let { mapper.readValue(it, DiagramLayout::class.java) }
		}
		diagramLayoutMainFlow?.let { diagramLayout?.add(0, it) }
		val layoutActivities = diagramLayout
			?.flatMap { layout ->
				layout.elements?.diagramElements
					?.filter { de ->
						(pcNamesFromProcedures + pcNamesFromMainFlow).map { it.referenceName }.contains(de.reference)
					}
					?.map { de -> Activity(uid = de.uid!!, reference = de.reference!!) }
					?: emptyList()
			}
			?.flatMap {
				diagramLayout.flatMap { dl ->
					dl.connections?.diagramConnections
						?.map { dc ->
							val exitName = dc.endPoints?.points?.firstOrNull { p -> p.elementRef == it.uid }?.exitPointRef ?: ""
							val anotherUid = dc.endPoints?.points?.firstOrNull { p -> p.elementRef != exitName }?.elementRef ?: ""
							val toActivity = dl.elements?.diagramElements
								?.firstOrNull { de -> de.uid == anotherUid }?.reference ?: ""
							LayoutActivity(
								exitName = exitName,
								name = selectedProcedure,
								reference = it.reference,
								toActivity = toActivity
							)
						}
						?.filter { la -> la.exitName.isNotEmpty() }
						?: emptyList()
				}
			}
			?.groupBy { it.reference }
			?.map { (reference, list) ->
				ActivitiesForMenu(
					list[0].name,
					reference,
					list.map { it.exitName },
					list.map { it.toActivity })
			}
			?: emptyList()

		return layoutActivities
	}


	private fun getAllConnectors(selectedProcess: String): List<DataSource> {
		val mapper = XmlMapper()

		val dataSourceActivityDefinitions = File(selectedProcess, "Procedures")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, DataSource::class.java) }
			}
			.filter { activity -> activity.connectorName != null }

		val propertiesFile = File(selectedProcess, "MainFlow")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, DataSource::class.java) }
			}
			.filter { activity -> activity.connectorName != null }

		return dataSourceActivityDefinitions + propertiesFile
	}


	private fun getConnectorReferences(selectedProcess: String, selectedConnectorName: Any): List<String> {
		return getAllConnectors(selectedProcess)
			.filter { activity -> activity.connectorName == selectedConnectorName }
			.map { activity -> activity.referenceName ?: "" }
	}


	private fun bindScrollSync(p1: VirtualizedScrollPane<*>, p2: VirtualizedScrollPane<*>) {
		val master = SimpleObjectProperty<VirtualizedScrollPane<*>?>(null)
		val idle = PauseTransition(FxDuration.millis(150.0)).apply {
			setOnFinished {
				if (!suspendSync.get()) {
					master.set(null)
				}
			}
		}

		fun ratioOf(p: VirtualizedScrollPane<*>): Double {
			val total = p.totalHeightEstimate
			if (total <= 0.0) return 0.0
			return (p.estimatedScrollY / total).coerceIn(0.0, 1.0)
		}

		fun applyRatio(src: VirtualizedScrollPane<*>, dst: VirtualizedScrollPane<*>) {
			val total = dst.totalHeightEstimate
			if (total <= 0.0) {
				return
			}
			val targetY = ratioOf(src) * total
			if (abs(dst.estimatedScrollY - targetY) < 0.5) {
				return
			}
			dst.scrollYToPixel(targetY)
		}

		fun wire(src: VirtualizedScrollPane<*>, dst: VirtualizedScrollPane<*>) {
			src.addEventFilter(ScrollEvent.SCROLL) {
				if (suspendSync.get()) return@addEventFilter
				master.set(src)
				applyRatio(src, dst)
				idle.playFromStart()
			}
			src.setOnMousePressed { if (!suspendSync.get()) master.set(src) }
			src.estimatedScrollYProperty().addListener { _, oldY, newY ->
				if (suspendSync.get()) {
					return@addListener
				}
				if (master.get() == null && abs(newY.toDouble() - oldY.toDouble()) > 0.5) {
					master.set(src)
				}
				if (master.get() === src) {
					applyRatio(src, dst)
					idle.playFromStart()
				}
			}
		}
		wire(p1, p2)
		wire(p2, p1)
	}

	private fun subscribeToChanges(
		area1: CodeArea,
		area2: CodeArea,
		prevBtn: Button,
		nextBtn: Button,
		diffProgress: ProgressIndicator
	) {
		diffProgress.visibleProperty().unbind()
		diffProgress.visibleProperty().bind(
			Bindings.createBooleanBinding(
				{ currentDiffTask?.isRunning == true },
				SimpleObjectProperty(currentDiffTask)
			)
		)
		area1.plainTextChanges().or(area2.plainTextChanges())
			.successionEnds(Duration.ofMillis(500))
			.subscribe {
				if (isUpdating.get()) return@subscribe
				currentDiffTask?.cancel()
				val origL = area1.text.lines()
				val origR = area2.text.lines()
				val task = object : Task<Triple<List<String>, List<String>, DiffResult>>() {
					override fun call(): Triple<List<String>, List<String>, DiffResult> {
						// Проверим отмену до и после тяжёлых шагов
						if (isCancelled) throw CancellationException()
						val (lA, rA) = padLinesForDiff(origL, origR)
						if (isCancelled) throw CancellationException()
						val diff = buildSpansAndNav(lA, rA)
						if (isCancelled) throw CancellationException()
						return Triple(lA, rA, diff)
					}
				}
				currentDiffTask = task
				task.setOnSucceeded {
					val (lA, rA, diff) = task.value
					isUpdating.set(true)
					try {
						val newL = lA.joinToString("\n")
						val newR = rA.joinToString("\n")
						if (area1.text != newL) area1.replaceText(newL)
						if (area2.text != newR) area2.replaceText(newR)
						val (spansL, spansR, nav, _, _) = diff
						area1.setStyleSpans(0, spansL)
						area2.setStyleSpans(0, spansR)

						diffNav.clear()
						diffNav.addAll(nav)
						diffIdx = if (nav.isEmpty()) -1 else 0
						prevBtn.isDisable = diffNav.isEmpty() || diffIdx <= 0
						nextBtn.isDisable = diffNav.isEmpty() || diffIdx >= diffNav.lastIndex

						if (nav.isNotEmpty()) {
							gotoDiff(0, area1, area2)
							updateNavButtons(prevBtn, nextBtn)
						} else {
							prevBtn.isDisable = true
							nextBtn.isDisable = true
						}
					} finally {
						isUpdating.set(false)
					}
				}
				task.setOnCancelled { System.err.println(it) }
				task.setOnFailed { System.err.println(it) }
				Thread(task).apply { isDaemon = true }.start()
			}
	}

	private fun recalculateDiff(
		area1: CodeArea,
		area2: CodeArea,
		prevBtn: Button,
		nextBtn: Button,
	) {
		if (area1.text.isNotBlank() && area2.text.isNotBlank()) {
			val (lA, rA) = padLinesForDiff(area1.text.lines(), area2.text.lines())
			val leftText = lA.joinToString("\n")
			val rightText = rA.joinToString("\n")
			if (area1.text != leftText) area1.replaceText(leftText)
			if (area2.text != rightText) area2.replaceText(rightText)

			val (spansL, spansR, nav, _, _) = buildSpansAndNav(lA, rA)
			area1.setStyleSpans(0, spansL)
			area2.setStyleSpans(0, spansR)
			area1.paragraphGraphicFactory = LineNumberFactory.get(area1)
			area2.paragraphGraphicFactory = LineNumberFactory.get(area2)

			diffNav.clear()
			diffNav.addAll(nav)
			diffIdx = if (diffNav.isEmpty()) -1 else 0
			if (diffNav.isNotEmpty()) {
				gotoDiff(0, area1, area2)
				updateNavButtons(prevBtn, nextBtn)
			} else {
				prevBtn.isDisable = true
				nextBtn.isDisable = true
			}
			highlightDiff(area1, lA, rA, changeStyle = "diff-delete")
			highlightDiff(area2, rA, lA, changeStyle = "diff-insert")
		}
	}


	/**
	 * area  – CodeArea, куда применяем стили
	 * original/other – содержимое по строкам (без переводов)
	 * changeStyle – стиль для изменённого куска в этой области:
	 *               для левой:  "diff-delete", для правой: "diff-insert"
	 */
	private fun highlightDiff(area: CodeArea, original: List<String>, other: List<String>, changeStyle: String) {
		val cleanOrig = original.map { line ->
			line.replace(ignoreAttrs) { m ->
				// m.groupValues[1] — это либо "UID", либо "ElementRef"
				"${m.groupValues[1]}=\"\""
			}
		}
		val cleanOther = other.map { line ->
			line.replace(ignoreAttrs) { m ->
				"${m.groupValues[1]}=\"\""
			}
		}
		val deltas = DiffUtils.diff(cleanOrig, cleanOther)
			.deltas
			.sortedBy { it.source.position }
		val spans = StyleSpansBuilder<Collection<String>>()
		var lineIdx = 0
		val lastLineIndex = original.lastIndex
		fun addPlain(len: Int) = spans.add(emptyList(), len)
		fun addChange(len: Int) = spans.add(listOf(changeStyle), len)
		fun lineLen(i: Int, s: String) = s.length + if (i < lastLineIndex) 1 else 0

		for (delta in deltas) {
			while (lineIdx < delta.source.position) {
				addPlain(lineLen(lineIdx, original[lineIdx]))
				lineIdx++
			}
			when (delta.type!!) {
				DeltaType.DELETE -> {
					delta.source.lines.forEachIndexed { j, s ->
						addChange(lineLen(lineIdx + j, s))
					}
					lineIdx += delta.source.lines.size
				}

				DeltaType.INSERT -> {
					// вставок в "original" нет — здесь ничего не добавляем
					// (вторая область подсветит их своим вызовом highlightDiff)
				}

				DeltaType.CHANGE -> {
					val src = delta.source.lines
					val tgt = delta.target.lines
					val m = max(src.size, tgt.size)
					for (j in 0 until m) {
						val s = src.getOrNull(j)
						val t = tgt.getOrNull(j)
						if (s != null && t != null) {
							val prefix = s.commonPrefixWith(t).length
							val maxSuf = min(s.length, t.length) - prefix
							var suf = 0
							while (suf < maxSuf && s[s.length - 1 - suf] == t[t.length - 1 - suf]) {
								suf++
							}

							if (prefix > 0) addPlain(prefix)
							val diffLen = s.length - prefix - suf
							if (diffLen > 0) addChange(diffLen)
							addPlain(suf + if (lineIdx + j < lastLineIndex) 1 else 0)
						} else if (s != null) {
							addChange(lineLen(lineIdx + j, s))
						} else {
							// строка есть только в target — в этой области нет чего красить
							// (правая/левая область подсветит на своём проходе)
						}
					}
					lineIdx += src.size
				}

				DeltaType.EQUAL -> {
					delta.source.lines.forEach { s ->
						addPlain(lineLen(lineIdx, s))
						lineIdx++
					}
				}
			}
		}
		while (lineIdx < original.size) {
			addPlain(lineLen(lineIdx, original[lineIdx]))
			lineIdx++
		}
		area.setStyleSpans(0, spans.create())
	}


	private fun lineStarts(lines: List<String>): IntArray {
		val starts = IntArray(lines.size) { 0 }
		var off = 0
		for (i in lines.indices) {
			starts[i] = off
			off += lines[i].length + if (i < lines.lastIndex) 1 else 0 // + '\n' кроме последней
		}
		return starts
	}

	/**
	 * Возвращает:
	 *  — StyleSpans для левой области,
	 *  — StyleSpans для правой области,
	 *  — список пар координат DiffNav (диапазоны отличий в обоих документах).
	 */
	private fun buildSpansAndNav(left: List<String>, right: List<String>): DiffResult {
		val deleted = mutableListOf<Int>()
		val inserted = mutableListOf<Int>()
		val cleanL = left.map { line ->
			line.replace(ignoreAttrs) { m -> "${m.groupValues[1]}=\"\"" }
		}
		val cleanR = right.map { line ->
			line.replace(ignoreAttrs) { m -> "${m.groupValues[1]}=\"\"" }
		}
		val deltas = DiffUtils.diff(cleanL, cleanR).deltas.sortedBy { it.source.position }
		val spansL = StyleSpansBuilder<Collection<String>>()
		val spansR = StyleSpansBuilder<Collection<String>>()
		val nav = mutableListOf<DiffNav>()
		val startsL = lineStarts(left)
		val startsR = lineStarts(right)
		var li = 0
		var ri = 0
		fun addPlainL(len: Int) = spansL.add(emptyList(), len)
		fun addPlainR(len: Int) = spansR.add(emptyList(), len)
		fun addDelL(len: Int) = spansL.add(listOf("diff-delete"), len)
		fun addInsR(len: Int) = spansR.add(listOf("diff-insert"), len)
		fun lenL(i: Int) = left[i].length + if (i < left.lastIndex) 1 else 0
		fun lenR(i: Int) = right[i].length + if (i < right.lastIndex) 1 else 0
		fun endChar(starts: IntArray, lines: List<String>, idx: Int) = starts[idx] + lines[idx].length

		for (d in deltas) {
			while (li < d.source.position) {
				addPlainL(lenL(li))
				li++
			}
			while (ri < d.target.position) {
				addPlainR(lenR(ri))
				ri++
			}
			val lCount = d.source.lines.size
			val rCount = d.target.lines.size
			val lStartIdx: Int? = if (lCount > 0) li else null
			val lEndIdx: Int? = if (lCount > 0) li + lCount - 1 else null
			val rStartIdx: Int? = if (rCount > 0) ri else null
			val rEndIdx: Int? = if (rCount > 0) ri + rCount - 1 else null

			when (d.type) {
				DeltaType.DELETE -> {
					d.source.lines.forEachIndexed { j, _ ->
						deleted += li + j
						addDelL(lenL(li + j))
					}
					li += lCount
				}

				DeltaType.INSERT -> {
					d.target.lines.forEachIndexed { j, _ ->
						inserted += ri + j
						addInsR(lenR(ri + j))
					}
					ri += rCount
				}

				DeltaType.CHANGE -> {
					if (lCount != rCount) {
						d.source.lines.indices.forEach { j -> deleted += li + j }
						d.target.lines.indices.forEach { j -> inserted += ri + j }
					} else {
						if (lCount > 0) deleted += li
						if (rCount > 0) inserted += ri
					}
					val m = max(lCount, rCount)
					for (j in 0 until m) {
						val sL = d.source.lines.getOrNull(j)
						val sR = d.target.lines.getOrNull(j)

						if (sL != null && sR != null) {
							val pref = sL.commonPrefixWith(sR).length
							val maxS = min(sL.length, sR.length) - pref
							var suf = 0
							while (suf < maxS && sL[sL.length - 1 - suf] == sR[sR.length - 1 - suf]) suf++

							if (pref > 0) addPlainL(pref)
							val diffL = sL.length - pref - suf
							if (diffL > 0) addDelL(diffL)
							addPlainL(suf + if (li + j < left.lastIndex) 1 else 0)

							if (pref > 0) addPlainR(pref)
							val diffR = sR.length - pref - suf
							if (diffR > 0) addInsR(diffR)
							addPlainR(suf + if (ri + j < right.lastIndex) 1 else 0)
						} else if (sL != null) {
							addDelL(lenL(li + j))
							addPlainR(0)
						} else {
							addPlainL(0)
							addInsR(lenR(ri + j))
						}
					}
					li += lCount
					ri += rCount
				}

				else -> {
					/* EQUAL не приходит сюда */
				}
			}
			val lStartChar = lStartIdx?.let { startsL[it] }
			val lEndChar = lEndIdx?.let { endChar(startsL, left, it) }
			val rStartChar = rStartIdx?.let { startsR[it] }
			val rEndChar = rEndIdx?.let { endChar(startsR, right, it) }

			if (lStartChar != null || rStartChar != null) {
				nav += DiffNav(lStart = lStartChar, lEnd = lEndChar, rStart = rStartChar, rEnd = rEndChar)
			}
		}

		// добить «хвост» равных зон
		while (li < left.size) {
			addPlainL(lenL(li))
			li++
		}
		while (ri < right.size) {
			addPlainR(lenR(ri))
			ri++
		}

		return DiffResult(spansL.create(), spansR.create(), nav.toList(), deleted, inserted)
	}


	private fun gotoDiff(step: Int, area1: CodeArea, area2: CodeArea) {
		if (diffNav.isEmpty()) return
		diffIdx = (diffIdx + step).coerceIn(0, diffNav.lastIndex)
		val d = diffNav[diffIdx]

		d.lStart?.let { s -> area1.selectRange(s, d.lEnd!!) }
		d.rStart?.let { s -> area2.selectRange(s, d.rEnd!!) }
		suspendSync.set(true)
		Platform.runLater {
			d.lStart?.let { s ->
				val p = area1.offsetToPosition(s, Bias.Forward).major
				area1.showParagraphAtCenter(p)
			}
			d.rStart?.let { s ->
				val p = area2.offsetToPosition(s, Bias.Forward).major
				area2.showParagraphAtCenter(p)
			}
			PauseTransition(FxDuration.millis(180.0)).apply {
				setOnFinished { suspendSync.set(false) }
			}.play()
		}
	}


	private fun wrapWithGrayFiller(scroll: VirtualizedScrollPane<*>): StackPane {
		val filler = Rectangle().apply {
			fill = Color.LIGHTGRAY
			isMouseTransparent = true
			isManaged = false
			widthProperty().bind(scroll.widthProperty())
			heightProperty().bind(
				Bindings.createDoubleBinding(
					{
						val H = scroll.height
						val y = scroll.estimatedScrollY
						val T = scroll.totalHeightEstimate
						maxOf(0.0, H + y - T)
					},
					scroll.heightProperty(),
					scroll.estimatedScrollYProperty(),
					scroll.totalHeightEstimateProperty()
				)
			)
			translateYProperty().bind(
				Bindings.createDoubleBinding(
					{
						val y = scroll.estimatedScrollY
						val T = scroll.totalHeightEstimate
						maxOf(0.0, T - y)
					},
					scroll.estimatedScrollYProperty(),
					scroll.totalHeightEstimateProperty()
				)
			)
		}
		return StackPane(scroll, filler)
	}


	private val diffExecutor = Executors.newSingleThreadExecutor { r ->
		Thread(r, "diff-pool").apply { isDaemon = true }
	}


	private fun searchAttribute(attributeName: String): List<String> {
		val mapper = XmlMapper()
		val xsltAttributeRegex = Regex(
			pattern = """<\s*xsl:attribute\s+name\s*=\s*"$attributeName"\s*>""",
			options = setOf(RegexOption.IGNORE_CASE)
		)
		val svAttributeRegex = Regex(
			"@([A-Za-z0-9_]+)$",
			options = setOf(RegexOption.IGNORE_CASE)
		)

		val mainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val procedures = File(selectedDirectory, "Procedures").walkTopDown().toList()

		val xsltAttributes = (mainFlow + procedures)
			.filter { it.isFile && it.extension == "xslt" }
			.filter { file -> xsltAttributeRegex.containsMatchIn(XmlReader.readXmlSafe(file)) }
			.map { file -> file.parentFile.name }
			.distinct()

		val svAttributes = (mainFlow + procedures)
			.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
			.mapNotNull { file ->
				val sv = mapper.readValue(file, SetValueActivity::class.java)
				if (sv.setValues?.items != null) {
					val xPaths = sv.setValues.items.mapNotNull { it.xPath }
					val anyMatch = xPaths.any {
						svAttributeRegex.find(it)?.groupValues?.get(0)?.removePrefix("@") == attributeName
					}
					if (anyMatch) {
						file.parentFile.name
					} else {
						null
					}
				} else {
					null
				}
			}
			.distinct()

		return (xsltAttributes + svAttributes)
	}


	private fun searchErasures(): List<Pair<String, List<String>>> {
		val mapper = XmlMapper()
		val commentRegex = Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL))
		val sameNameTemplateRegex = Regex(
			"""(?s)<\s*xsl:template\s+name\s*=\s*"([^"]+)"\s*>.*?<\s*xsl:element\s+name\s*="\1"\s*/>.*?</\s*xsl:template\s*>""",
			RegexOption.IGNORE_CASE
		)

		val erasuresInProcedures = File(selectedDirectory, "Procedures").walkTopDown().toList()
		val erasuresInMainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val result = (erasuresInMainFlow + erasuresInProcedures)
			.filter { it.isFile && it.extension.equals("xslt", ignoreCase = true) }
			.mapNotNull { xsltFile ->
				val text = commentRegex.replace(XmlReader.readXmlSafe(xsltFile), "")
				if (!sameNameTemplateRegex.containsMatchIn(text)) return@mapNotNull null
				val erasedNames = sameNameTemplateRegex.findAll(text)
					.map { it.groupValues[1] }
					.toSet()
				val propsFile = xsltFile.parentFile.resolve("Properties.xml")
				if (!propsFile.exists()) {
					return@mapNotNull null
				}
				val def = propsFile.inputStream().use { mapper.readValue(it, DataMapping::class.java) }
				val names = def.referredDocuments?.items
					?.filter { it.access.equals("InOut", true) && it.referenceName in erasedNames }
					?.map { it.referenceName }
					?: emptyList()
				xsltFile.parentFile.name to names
			}
			.toList()

		return result
	}


	private fun padLinesForDiff(
		left: List<String>,
		right: List<String>
	): Pair<List<String>, List<String>> {
		fun List<String>.clean() = map { s ->
			s.replace(ignoreAttrs) { m -> "${m.groupValues[1]}=\"\"" }
		}

		val cleanL = left.clean()
		val cleanR = right.clean()

		// 2. Считаем отличия
		val deltas = DiffUtils
			.diff(cleanL, cleanR)
			.deltas
			.sortedBy { it.source.position }

		val outL = mutableListOf<String>()
		val outR = mutableListOf<String>()

		var li = 0
		var ri = 0

		fun emitCommon(toLi: Int, toRi: Int) {
			while (li < toLi && ri < toRi) {
				outL += left[li]
				outR += right[ri]
				li++
				ri++
			}
		}

		for (d in deltas) {
			emitCommon(d.source.position, d.target.position)

			when (d.type) {
				DeltaType.DELETE -> {
					repeat(d.source.lines.size) {
						outL += left[li]
						outR += ""
						li++
					}
				}

				DeltaType.INSERT -> {
					repeat(d.target.lines.size) {
						outL += ""
						outR += right[ri]
						ri++
					}
				}

				DeltaType.CHANGE -> {
					val lCnt = d.source.lines.size
					val rCnt = d.target.lines.size
					val maxCnt = max(lCnt, rCnt)
					for (j in 0 until maxCnt) {
						outL += if (j < lCnt) left[li + j] else ""
						outR += if (j < rCnt) right[ri + j] else ""
					}
					li += lCnt
					ri += rCnt
				}

				else -> {
				}
			}
		}
		while (li < left.size || ri < right.size) {
			outL += if (li < left.size) left[li++] else ""
			outR += if (ri < right.size) right[ri++] else ""
		}

		return outL to outR
	}


	private fun updateNavButtons(prevBtn: Button, nextBtn: Button) {
		val empty = diffNav.isEmpty()
		prevBtn.isDisable = empty || diffIdx <= 0
		nextBtn.isDisable = empty || diffIdx >= diffNav.lastIndex
	}


	private fun showSearchWindow(margeArea1: CodeArea, margeArea2: CodeArea) {
		val dialog = Stage().apply {
			initModality(Modality.APPLICATION_MODAL)
			title = "Поиск"
		}

		val searchField = TextField().apply { promptText = "Введите текст поиска" }
		val prevBtn = Button("◀️").apply { isDisable = true }
		val nextBtn = Button("▶️").apply { isDisable = true }

		val occurrences = mutableListOf<IntRange>()
		var currentIndex = -1

		fun updateButtons() {
			prevBtn.isDisable = occurrences.isEmpty() || currentIndex <= 0
			nextBtn.isDisable = occurrences.isEmpty() || currentIndex >= occurrences.lastIndex
		}

		fun highlightAll(area: CodeArea, query: String) {
			val text = area.text
			val pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE)
			val matcher = pattern.matcher(text)
			val spansBuilder = StyleSpansBuilder<Collection<String>>()
			var lastEnd = 0
			occurrences.clear()
			while (matcher.find()) {
				spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd)
				spansBuilder.add(Collections.singleton("search-highlight"), matcher.end() - matcher.start())
				occurrences.add(matcher.start() until matcher.end())
				lastEnd = matcher.end()
			}
			spansBuilder.add(Collections.emptyList(), text.length - lastEnd)
			area.setStyleSpans(0, spansBuilder.create())
		}

		fun selectOccurrence(area: CodeArea, idx: Int) {
			val range = occurrences[idx]
			val pos = area.offsetToPosition(range.start, Bias.Forward).major
			area.showParagraphAtCenter(pos)
			area.selectRange(range.start, range.endInclusive + 1)
		}

		val searchBtn = Button("Найти").apply {
			setOnAction {
				val query = searchField.text
				if (query.isNullOrBlank()) return@setOnAction
				val active = if (margeArea1.isFocused) margeArea1 else margeArea2
				highlightAll(active, query)
				currentIndex = if (occurrences.isEmpty()) -1 else 0
				updateButtons()
				if (currentIndex >= 0) selectOccurrence(active, currentIndex)
			}
		}

		prevBtn.setOnAction {
			if (currentIndex > 0) {
				currentIndex--
				val active = if (margeArea1.isFocused) margeArea1 else margeArea2
				selectOccurrence(active, currentIndex)
				updateButtons()
			}
		}

		nextBtn.setOnAction {
			if (currentIndex < occurrences.lastIndex) {
				currentIndex++
				val active = if (margeArea1.isFocused) margeArea1 else margeArea2
				selectOccurrence(active, currentIndex)
				updateButtons()
			}
		}

		val controls = HBox(4.0, searchField, searchBtn, prevBtn, nextBtn).apply {
			padding = Insets(10.0)
		}

		dialog.scene = Scene(VBox(controls), 400.0, 80.0)
		dialog.showAndWait()
	}


	private fun searchUnusedFo(): List<String> {
		val objectIDRegex = Regex(
			"""<form:include[^>]*objectID="([^"]+)"[^>]*/>""",
			RegexOption.IGNORE_CASE
		)

		val allFo = File(selectedDirectory, "FormObjects")
			.walkTopDown()
			.toList()
			.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
			.map { xmlFile -> xmlFile.nameWithoutExtension }

		val foInMainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val foInProcedures = File(selectedDirectory, "Procedures").walkTopDown().toList()
		val allUsedFo = (foInMainFlow + foInProcedures)
			.filter { it.isFile && it.name == "XForm.xml" }
			.flatMap { xmlFile ->
				val text = XmlReader.readXmlSafe(xmlFile)
				val matches = objectIDRegex.findAll(text).map { it.groupValues[1] }.toList()
				matches
			}
			.distinct()

		return (allFo - allUsedFo.toSet()) + (allUsedFo - allFo.toSet())
	}

	private fun searchUnusedBr(): List<String?> {
		val mapper = XmlMapper()
			.registerKotlinModule()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

		val allBrs = File(selectedDirectory, "BusinessRules")
			.walkTopDown()
			.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
			.map { xmlFile -> mapper.readValue(XmlReader.readXmlSafe(xmlFile), BusinessRule::class.java).businessRuleID }
			.toList()

		val activitiesInMainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val activitiesInProcedures = File(selectedDirectory, "Procedures").walkTopDown().toList()

		val segmentationTrees = (activitiesInMainFlow + activitiesInProcedures)
			.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
			.mapNotNull { xmlFile -> mapper.readValue(XmlReader.readXmlSafe(xmlFile), SegmentationTree::class.java) }
			.filter { br -> br.rules != null }
			.distinct()
			.toList()

		val forms = (activitiesInMainFlow + activitiesInProcedures)
			.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
			.mapNotNull { xmlFile -> mapper.readValue(XmlReader.readXmlSafe(xmlFile), Form::class.java) }
			.filter { f -> f.exitTimeouts?.any { it.exitBusinessRules != null } == true }

		val dispatches = (activitiesInMainFlow + activitiesInProcedures)
			.toList()
			.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
			.mapNotNull { xmlFile -> mapper.readValue(XmlReader.readXmlSafe(xmlFile), Dispatch::class.java) }
			.filter { dr -> dr.dispatchRuleIDs?.dispatchTests?.any { it.businessRuleId != null } == true }

		return allBrs
			.minus(segmentationTrees.flatMap { st -> st.rules?.ruleList?.map { it.ruleID } ?: emptyList() }.toSet())
			.minus(forms.flatMap { fm -> fm.exitTimeouts?.map { it.exitBusinessRules } ?: emptyList() }.toSet())
			.minus(dispatches.flatMap { dr -> dr.dispatchRuleIDs?.dispatchTests?.mapNotNull { it.businessRuleId } ?: emptyList() }
				.toSet())
	}


	private fun searchUnusedProcedures(): List<String> {
		val dir = selectedDirectory ?: return emptyList()
		val files = File(dir, "Procedures").list() ?: arrayOf()
		val result = mutableListOf<String>()
		for (name in files) {
			val acts = getProceduresActivities(name)
			if (acts.isEmpty()) {
				result.add(name)
			}
		}
		return result
	}


	private fun searchUnusedActivities(): List<EmptyActivity> {
		val mapper = XmlMapper()

		val activitiesInMainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val activitiesInProcedures = File(selectedDirectory, "Procedures").walkTopDown().toList()

		val result = (activitiesInMainFlow + activitiesInProcedures)
			.filter {
				it.isFile && it.name.equals("Layout.xml", ignoreCase = true)
			}
			.flatMap { file ->
				val layout = mapper.readValue(file, DiagramLayout::class.java)
				val connectionStats =
					mutableMapOf<String, MutableMap<String, Int>>().withDefault { mutableMapOf("in" to 0, "out" to 0) }

				layout.connections?.diagramConnections?.forEach { connection ->
					val endpoints = connection.endPoints?.points
					if ((endpoints?.size ?: 0) >= 2) {
						val from = endpoints?.get(0)?.elementRef
						val to = endpoints?.get(1)?.elementRef
						if (from != null) {
							connectionStats.getOrPut(from) { mutableMapOf("in" to 0, "out" to 0) }["out"] =
								connectionStats.getValue(from)["out"]!! + 1
						}
						if (to != null) {
							connectionStats.getOrPut(to) { mutableMapOf("in" to 0, "out" to 0) }["in"] =
								connectionStats.getValue(to)["in"]!! + 1
						}
					}
				}

				layout.elements?.diagramElements?.mapNotNull { el ->
					val stats = connectionStats[el.uid] ?: mapOf("in" to 0, "out" to 0)
					val inCount = stats["in"] ?: 0
					val outCount = stats["out"] ?: 0
					if (
						(inCount + outCount < 2)
						&& el.reference?.startsWith("EP_") != true /*FIXME проверять по типу активности*/
						&& (el.reference?.startsWith("PR_") != true && outCount == 0) /*FIXME проверять по типу активности*/
					) {
						EmptyActivity("${file.parentFile.name}\\${el.reference}", el.uid, inCount, outCount)
					} else {
						null
					}
				} ?: emptyList()
			}
			.distinct()
		return result
	}


	private fun searchInvalidCharacters(): List<InvalidCharacter> {
		val nonEnglishRegex = Regex("""[^\x00-\x7F]""")
		val tagRegex = Regex("""</?\s*([^\s>/]+)""")
		val attrRegex = Regex("""\b([^\s=:/><]+)\s*=""")
		val xpathAttrRegex = Regex("""\b(?:select|test|match|use)\s*=\s*(['"])(.*?)\1""")
		val xpathRegex = Regex("""/([^\s/\[\]@'"=<>()]+)""")
		val commentRegex = Regex("""<!--.*?-->""", setOf(RegexOption.DOT_MATCHES_ALL))
		val stringLiteralRegex = Regex("""(['"])([^'"\r\n]*)\1""")

		val mainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val procedures = File(selectedDirectory, "Procedures").walkTopDown().toList()

		val result = (mainFlow + procedures)
			.filter { it.isFile && it.extension == "xslt" }
			.flatMap { file ->
				val input = commentRegex.replace(XmlReader.readXmlSafe(file), "")
				val allMatches = mutableListOf<String>()
				tagRegex.findAll(input).forEach { allMatches.add(it.groupValues[1]) }
				attrRegex.findAll(input).forEach { allMatches.add(it.groupValues[1]) }
				xpathAttrRegex.findAll(input).forEach { m ->
					var expr = m.groupValues[2]
					expr = stringLiteralRegex.replace(expr, "\"\"")
					xpathRegex.findAll(expr).forEach { allMatches.add(it.groupValues[1]) }
				}
				return@flatMap allMatches
					.filterNot { it.contains("&gt;") || it.contains("&lt;") || it.contains("&amp;") }
					.filter { nonEnglishRegex.containsMatchIn(it) }
					.map { invalid -> InvalidCharacter("${file.parentFile.name}\\${file.name}", invalid) }
			}
		return result
	}


	private fun searchInvalidNaming(): List<Triple<String, String, String>> {
		val regex = Regex("""ReferenceName\s*=\s*"([^"]+)"""")
		val procedureNoRegex = Regex("^[A-Z]+_(\\d+)")

		val mainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val procedures = File(selectedDirectory, "Procedures").walkTopDown().toList()

		val result = (mainFlow + procedures)
			.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
			.mapNotNull { file ->
				val header = XmlReader.readXmlSafe(file).lines().first().replace("\uFEFF", "")
				val match = regex.find(header)
				val referenceName = match?.groupValues?.get(1)!!
				val activityType = when {
					header.startsWith("<BizRuleActivityDefinition") -> ActivityType.BIZ_RULE
					header.startsWith("<DataSourceActivityDefinition") -> ActivityType.DATA_SOURCE
					header.startsWith("<DispatchActivityDefinition") -> ActivityType.DISPATCH
					header.startsWith("<FormActivityDefinition") -> ActivityType.FORM
					header.startsWith("<MappingActivityDefinition") -> ActivityType.DATA_MAPPING
					header.startsWith("<ProcedureCallActivityDefinition") -> ActivityType.PROCEDURE_CALL
					header.startsWith("<SegmentationTreeActivityDefinition") -> ActivityType.SEGMENTATION_TREE
					header.startsWith("<SetValueActivityDefinition") -> ActivityType.SET_VALUE
					header.startsWith("<WaitActivityDefinition") -> ActivityType.WAIT
					header.startsWith("<ProcedureReturnActivityDefinition") -> ActivityType.PROCEDURE_RETURN
					header.startsWith("<EndProcessActivityDefinition") -> ActivityType.END_PROCEDURE
					header.startsWith("<SendEMailActivityDefinition") -> ActivityType.SEND_EMAIL
					header.startsWith("<PhaseActivityDefinition") -> ActivityType.SET_PHASE
					else -> ActivityType.UNKNOWN
				}
				val parentProcedureNo = if (file.parentFile.parentFile.name == "MainFlow") {
					"0"
				} else {
					procedureNoRegex.find(file.parentFile.parentFile.name)?.groupValues?.get(1)
				}
				val procedureNo = procedureNoRegex.find(referenceName)?.groupValues?.get(1)
				if (!referenceName.startsWith("${activityType.prefix}_") || procedureNo != parentProcedureNo) {
					Triple(file.parentFile.parentFile.name, referenceName, activityType.name)
				} else {
					null
				}
			}
		return result
	}


	private fun searchEmptyExits(searchActivityType: String, ignoreFailed: Boolean): List<Pair<String, String>> {
		val mapper = XmlMapper()

		val activitiesInMainFlow = File(selectedDirectory, "MainFlow").walkTopDown().toList()
		val activitiesInProcedures = File(selectedDirectory, "Procedures").walkTopDown().toList()

		val layouts = (activitiesInMainFlow + activitiesInProcedures)
			.filter { it.isFile && it.name.equals("Layout.xml", ignoreCase = true) }
			.map { file -> mapper.readValue(XmlReader.readXmlSafe(file), DiagramLayout::class.java) }

		val result = mutableListOf<Pair<String, String>>()

		when (ActivityType.valueOf(searchActivityType)) {
			ActivityType.FORM -> {
				val forms = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<FormActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, Form::class.java) }

				val formNames = forms.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in formNames } ?: emptyList()

					forms.forEach { fm ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == fm.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == fm.referenceName }) {
							val allExits = fm.commands?.activityCommands
								?.filter { it.clazz != "BuiltIn" }
								?.map { it.value }
								?.toSet()
								?.plus(ActivityType.FORM.exits)
								?: emptySet()
							val notUsed = allExits - usedExitRefs
							result += notUsed.map { fm.referenceName to (it ?: "") }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.BIZ_RULE -> {
				val bizRules = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<BizRuleActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, BizRule::class.java) }

				val bizRuleNames = bizRules.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in bizRuleNames } ?: emptyList()

					bizRules.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.BIZ_RULE.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.SEGMENTATION_TREE -> {
				val segmentationTrees = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<SegmentationTreeActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, SegmentationTree::class.java) }

				val segmentationTreesReferenceNames = segmentationTrees.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in segmentationTreesReferenceNames } ?: emptyList()

					segmentationTrees.forEach { st ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == st.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == st.referenceName }) {
							val allExits = st.rules?.ruleList
								?.map { it.connectionID }
								?.toSet()
								?.plus(ActivityType.SEGMENTATION_TREE.exits)
								?: emptySet()
							val notUsed = allExits - usedExitRefs
							result += notUsed.map { st.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.DATA_SOURCE -> {
				val dataSources = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<DataSourceActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, DataSource::class.java) }

				val dsNames = dataSources.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in dsNames } ?: emptyList()

					dataSources.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.DATA_SOURCE.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.DATA_MAPPING -> {
				val dataMappings = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<MappingActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, DataMapping::class.java) }

				val dmNames = dataMappings.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in dmNames } ?: emptyList()

					dataMappings.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.DATA_MAPPING.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.SET_VALUE -> {
				val setValueActivities = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<SetValueActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, SetValueActivity::class.java) }

				val setValueNames = setValueActivities.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in setValueNames } ?: emptyList()

					setValueActivities.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.SET_VALUE.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.PROCEDURE_CALL -> {
				val procedures = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.mapNotNull { file ->
						val xml = XmlReader.readXmlSafe(file)
						if (xml.startsWith("<ProcedureCallActivityDefinition")) {
							mapper.readValue(xml, ProcedureCall::class.java) to file
						} else {
							null
						}
					}

				val segmentationTreesReferenceNames = procedures.map { it.first.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in segmentationTreesReferenceNames } ?: emptyList()

					procedures.forEach { (procedure, file) ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == procedure.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == procedure.referenceName }) {
							val allExits = File("${selectedDirectory}/Procedures/${procedure.procedureToCall}/Layout.xml")
								.let { XmlReader.readXmlSafe(it) }
								.let { mapper.readValue(it, DiagramLayout::class.java) }
								.exits?.values
								?: emptyList()
							val notUsed = allExits - usedExitRefs
							result += notUsed.map { procedure.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.DISPATCH -> {
				val dispatches = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<DispatchActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, Dispatch::class.java) }

				val dispatchNames = dispatches.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in dispatchNames } ?: emptyList()

					dispatches.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.DISPATCH.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.WAIT -> {
				val waits = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<WaitActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, Wait::class.java) }

				val waitNames = waits.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in waitNames } ?: emptyList()

					waits.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.WAIT.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.SEND_EMAIL -> {
				val sendEmails = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<SendEMailActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, SendEmail::class.java) }

				val sendEmailNames = sendEmails.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in sendEmailNames } ?: emptyList()

					sendEmails.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.SEND_EMAIL.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			ActivityType.SET_PHASE -> {
				val setPhases = (activitiesInMainFlow + activitiesInProcedures)
					.filter { it.isFile && it.name.equals("Properties.xml", ignoreCase = true) }
					.map { XmlReader.readXmlSafe(it) }
					.filter { it.startsWith("<PhaseActivityDefinition") }
					.mapNotNull { xmlFile -> mapper.readValue(xmlFile, SetPhase::class.java) }

				val setPhaseNames = setPhases.map { it.referenceName }

				layouts.forEach { layout ->
					val elements = layout.elements?.diagramElements
						?.filter { it.reference in setPhaseNames } ?: emptyList()

					setPhases.forEach { br ->
						val usedExitRefs = layout.connections?.diagramConnections
							?.flatMap { con ->
								con.endPoints?.points
									?.filter {
										con.endPoints.points
											.mapNotNull { it.elementRef }
											.contains(elements.firstOrNull { it.reference == br.referenceName }?.uid)
									}
									?.mapNotNull { it.exitPointRef }
									?: emptyList()
							}
							?.toSet()
							?: emptySet()
						if (elements.any { it.reference == br.referenceName }) {
							val notUsed = ActivityType.SET_PHASE.exits - usedExitRefs
							result += notUsed.map { br.referenceName to it }
						}
					}
				}

				return result.filter { (_, exit) -> !ignoreFailed || exit != "Failed" }
			}

			else -> {
				return listOf()
			}
		}
	}


	private fun commitComboValue(cb: ComboBox<String>, allItems: List<String>) {
		val txt = cb.editor.text?.trim().orEmpty()
		if (txt.isEmpty()) return
		val match = allItems.firstOrNull { it.equals(txt, ignoreCase = true) }
		cb.value = match
	}


	private fun addingNewSplit(layout: String, from: String, fromExit: String, to: String, toExit: String) {
		val mapper = XmlMapper()

		val dir =
			if (layout.isNotEmpty() && from.isNotEmpty() && fromExit.isNotEmpty() && to.isNotEmpty() && toExit.isNotEmpty()) {
				if (layout == "MainFlow") {
					File(selectedDirectory, "MainFlow")
				} else {
					File(selectedDirectory, "Procedures/$layout")
				}
			} else {
				return
			}

		val layoutFile = File(dir, "Layout.xml")

		val diagramLayout = mapper.readValue(layoutFile, DiagramLayout::class.java)

		val fromUid = diagramLayout
			.elements?.diagramElements
			?.first { el -> el.reference == from }
			?.uid

		val toUid = diagramLayout
			.elements?.diagramElements
			?.first { el -> el.reference == to }
			?.uid

		diagramLayout.connections?.diagramConnections
			?.add(
				DiagramConnection(
					uid = UUID.randomUUID().toString(),
					splits = Splits(emptyList()),
					endPoints = EndPoints(
						listOf(
							DiagramEndPoint(elementRef = fromUid, exitPointRef = fromExit),
							DiagramEndPoint(elementRef = toUid, exitPointRef = toExit),
						)
					)
				)
			)

		toPrettyXml(diagramLayout, layoutFile, XmlReader.getEncoding(layoutFile.readBytes()))
	}


	private fun writeBom(os: OutputStream, cs: Charset) {
		when (cs) {
			Charsets.UTF_16BE -> os.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
			Charsets.UTF_16LE -> os.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
			Charsets.UTF_8 -> os.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
		}
	}


	private fun toPrettyXml(obj: Any, file: File, charset: Charset) {
		val mapper = XmlMapper.builder()
			.defaultUseWrapper(false)
			.build()
			.apply {
				registerKotlinModule()
				enable(SerializationFeature.INDENT_OUTPUT)
				setSerializationInclusion(JsonInclude.Include.ALWAYS)
				enable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS)
			}

		file.outputStream().use { os ->
			writeBom(os, charset)
			OutputStreamWriter(os, charset).use { w ->
				val encLabel = when (charset.name().uppercase()) {
					"UTF-16LE" -> "utf-16"
					"UTF-16BE" -> "utf-16"
					else -> charset.name().lowercase()
				}
				w.write("""<?xml version="1.0" encoding="$encLabel"?>""")
				w.write("\n")
				val xml = mapper.writeValueAsString(obj)
					.replace(Regex("/>"), " />")
					.trimEnd()
				w.write(xml)
			}
		}
	}


	private fun reportToExcel() {
		val progressStage = Stage().apply {
			initModality(Modality.APPLICATION_MODAL)
			title = "Формирование отчета"
		}
		val progressBar = ProgressBar(0.0).apply { prefWidth = 300.0 }
		val statusLabel = Label("Подготовка...")
		val vbox = VBox(10.0, statusLabel, progressBar).apply { padding = Insets(20.0) }
		progressStage.scene = Scene(vbox)
		progressStage.show()

		val task = object : Task<File>() {
			override fun call(): File {
				val workbook = XSSFWorkbook()

				val steps: List<Pair<String, () -> Unit>> = listOf(
					"Unused FO" to {
						val sheet = workbook.createSheet("Unused FO")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("FO")
						searchUnusedFo().forEachIndexed { i, value ->
							sheet.createRow(i + 1).createCell(0).setCellValue(value)
						}
					},
					"Unused BR" to {
						val sheet = workbook.createSheet("Unused BR")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("BusinessRule")
						searchUnusedBr().forEachIndexed { i, value ->
							sheet.createRow(i + 1).createCell(0).setCellValue(value ?: "")
						}
					},
					"Unused Procedures" to {
						val sheet = workbook.createSheet("Unused Procedures")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("Procedure")
						searchUnusedProcedures().forEachIndexed { i, value ->
							sheet.createRow(i + 1).createCell(0).setCellValue(value)
						}
					},
					"Unused Activities" to {
						val sheet = workbook.createSheet("Unused Activities")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("Activity")
						header.createCell(1).setCellValue("In Count")
						header.createCell(2).setCellValue("Out Count")
						searchUnusedActivities().forEachIndexed { i, value ->
							val row = sheet.createRow(i + 1)
							row.createCell(0).setCellValue(value.activity)
							row.createCell(1).setCellValue(value.inCount.toDouble())
							row.createCell(2).setCellValue(value.outCount.toDouble())
						}
					},
					"Invalid Characters" to {
						val sheet = workbook.createSheet("Invalid Characters")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("Activity")
						header.createCell(1).setCellValue("Value")
						searchInvalidCharacters().forEachIndexed { i, value ->
							val row = sheet.createRow(i + 1)
							row.createCell(0).setCellValue(value.activity)
							row.createCell(1).setCellValue(value.value)
						}
					},
					"Erasures" to {
						val sheet = workbook.createSheet("Erasures")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("Procedure")
						header.createCell(1).setCellValue("Names")
						searchErasures().forEachIndexed { i, (proc, names) ->
							val row = sheet.createRow(i + 1)
							row.createCell(0).setCellValue(proc)
							row.createCell(1).setCellValue(names.joinToString(", "))
						}
					},
					"Invalid Naming" to {
						val sheet = workbook.createSheet("Invalid Naming")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("Procedure")
						header.createCell(1).setCellValue("Wrong Name")
						header.createCell(2).setCellValue("Type")
						searchInvalidNaming().forEachIndexed { i, triple ->
							val row = sheet.createRow(i + 1)
							row.createCell(0).setCellValue(triple.first)
							row.createCell(1).setCellValue(triple.second)
							row.createCell(2).setCellValue(triple.third)
						}
					},
					"Empty Exits" to {
						val sheet = workbook.createSheet("Empty Exits")
						val header = sheet.createRow(0)
						header.createCell(0).setCellValue("Activity")
						header.createCell(1).setCellValue("Exit")
						ActivityType.values().flatMap { searchEmptyExits(it.name, false) }
							.forEachIndexed { i, (act, exit) ->
								val row = sheet.createRow(i + 1)
								row.createCell(0).setCellValue(act)
								row.createCell(1).setCellValue(exit)
							}
					}
				)

				for ((i, step) in steps.withIndex()) {
					if (isCancelled) break
					updateMessage("Формируется: ${step.first}")
					step.second.invoke()
					updateProgress(i + 1L, steps.size.toLong())
				}

				val file = File("report.xlsx")
				FileOutputStream(file).use { workbook.write(it) }
				workbook.close()
				return file
			}
		}

		progressBar.progressProperty().bind(task.progressProperty())
		statusLabel.textProperty().bind(task.messageProperty())

		task.setOnSucceeded {
			progressStage.close()
			val file = task.value
			Alert(Alert.AlertType.INFORMATION).apply {
				title = "Готово"
				headerText = null
				contentText = "Файл сохранен: ${file.absolutePath}"
				showAndWait()
			}
		}
		task.setOnFailed {
			progressStage.close()
			Alert(Alert.AlertType.ERROR).apply {
				title = "Ошибка"
				headerText = null
				contentText = "Не удалось сформировать отчет: ${task.exception?.message}"
				showAndWait()
			}
		}

		Thread(task).apply { isDaemon = true }.start()
	}


	override fun stop() {
		super.stop()
		subscription?.unsubscribe()
		diffExecutor.shutdown()
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
		return File(configDir, SAVE_FILE_NAME)
	}


	private fun saveSelectedPath(path: String) {
		val props = Properties()
		props[SAVE_FOLDER_NAME] = path
		getConfigFile().outputStream().use { props.store(it, null) }
	}


	private fun loadSelectedPath(): String? {
		val file = getConfigFile()
		if (!file.exists()) return null
		val props = Properties()
		file.inputStream().use { props.load(it) }
		return props.getProperty(SAVE_FOLDER_NAME)
	}


	companion object {
		private const val SAVE_FILE_NAME = "config.properties"
		private const val SAVE_FOLDER_NAME = "selectedDirectory"
		private val ignoreAttrs = Regex("\\b(UID|ElementRef)=\"[^\"]*\"")
	}

}


fun main() {
	Application.launch(RCrifLayoutTool::class.java)
}