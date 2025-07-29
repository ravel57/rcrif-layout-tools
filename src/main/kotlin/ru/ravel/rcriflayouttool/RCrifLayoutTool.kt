package ru.ravel.rcriflayouttool

import org.fxmisc.richtext.model.TwoDimensional.Bias
import com.fasterxml.jackson.databind.DeserializationFeature
import com.github.difflib.DiffUtils
import org.fxmisc.richtext.model.StyleSpansBuilder
import com.github.difflib.patch.DeltaType
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.model.StyleSpans
import java.time.Duration
import org.reactfx.EventStream
import org.reactfx.Subscription
import ru.ravel.rcriflayouttool.dto.*
import ru.ravel.rcriflayouttool.model.connectorproperties.DataSourceActivityDefinition
import ru.ravel.rcriflayouttool.model.layout.DiagramLayout
import ru.ravel.rcriflayouttool.model.procedureproperties.ProcedureCallActivityDefinition
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min


/*
TODO
	Нумерация строк
*/

class RCrifLayoutTool : Application() {

	private var selectedDirectory: File? = null
	private var layoutActivitiesCache: List<ActivitiesForMenu> = emptyList()
	private var subscription: Subscription? = null

	private val diffNav = mutableListOf<DiffNav>()
	private var diffIdx = -1


	override fun start(stage: Stage) {
		val allProcedures = FXCollections.observableArrayList<String>()
		val filteredProcedures = FilteredList(allProcedures) { true }

		val allConnectors = FXCollections.observableArrayList<String>()
		val filteredConnectors = FilteredList(allConnectors) { true }

		val allMarge = FXCollections.observableArrayList<String>()
		val filteredMarge = FilteredList(allMarge) { true }

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
			columns.addAll(searchProcedureTableColumn)
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
					menu.items.addAll(items.distinct().map { MenuItem(it) })
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
						procedureTableView.items.clear()
						procedureTableView.items.addAll(activities.map { ParamRow(SimpleStringProperty(it.reference)) })
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
				allMarge.setAll(File(selectedDirectory, "Procedures").list()
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
					allMarge.setAll(File(selectedDirectory, "Procedures").list()
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
			10.0,
			HBox(Label("Название процедуры:"), procedureComboBox).apply { padding = Insets(5.0) },
			procedureTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Поиск коннекторов */
		val connectorTableColumn = TableColumn<ParamRow, String>("Название в активности").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val connectorTableView = TableView<ParamRow>().apply {
			columns.addAll(connectorTableColumn)
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
						connectorTableView.items.clear()
						connectorTableView.items.addAll(getConnectorReferences(folderField.text, newVal).map {
							ParamRow(SimpleStringProperty(it))
						})
					}
				}
			}
		}
		val connectorsTabContent = VBox(
			10.0,
			HBox(Label("Название коннектора:"), connectorsComboBox).apply { padding = Insets(5.0) },
			connectorTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Marge */
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
					}
				}
			}
		}

		val margeArea1 = CodeArea()
		val margeArea2 = CodeArea()
		val prevBtn = Button("◀").apply { isDisable = true; setOnAction { gotoDiff(-1, margeArea1, margeArea2) } }
		val nextBtn = Button("▶").apply { isDisable = true; setOnAction { gotoDiff(+1, margeArea1, margeArea2) } }
		val margePane1 = VirtualizedScrollPane(margeArea1).apply {
			maxHeight = Double.MAX_VALUE
		}
		val margePane2 = VirtualizedScrollPane(margeArea2).apply {
			maxHeight = Double.MAX_VALUE
		}
		bindScrollSync(margePane1, margePane2)
		subscribeToChanges(margeArea1, margeArea2, prevBtn, nextBtn, margeArea1, margeArea2)

		val panesHBox = HBox(
			4.0,
			Label("Layout: "),
			mergeComboBox,
			prevBtn,
			nextBtn
		).apply {
			padding = Insets(5.0)
		}

		val contentBox = HBox(4.0, margePane1, margePane2).apply {
			spacing = 4.0
			isFillHeight = true
			HBox.setHgrow(margePane1, Priority.ALWAYS)
			HBox.setHgrow(margePane2, Priority.ALWAYS)
			margePane1.maxHeight = Double.MAX_VALUE
			margePane2.maxHeight = Double.MAX_VALUE
		}

		val margeBp = BorderPane().apply {
			top = panesHBox
			center = contentBox
			padding = Insets(20.0)
		}
		VBox.setVgrow(margeBp, Priority.ALWAYS)

		/* Добавление связей */
		val emptyTabContent = VBox(20.0, Label("Пусто"))

		/* Сборка вкладок */
		val tabPane = TabPane(
			Tab("Поиск использования процедур", proceduresTabContent).apply { isClosable = false },
			Tab("Поиск использования коннекторов", connectorsTabContent).apply { isClosable = false },
			Tab("Layout marge", margeBp).apply { isClosable = false },
			Tab("Добавление связей", emptyTabContent).apply { isClosable = false },
		)

		VBox.setVgrow(tabPane, Priority.ALWAYS)

		val root = VBox(
			10.0,
			folderChooserPanel,
			tabPane
		).apply {
			padding = Insets(20.0)
		}

		stage.scene = Scene(root, 600.0, 500.0).apply {
			javaClass.classLoader.getResource("diff.css")?.let {
				stylesheets.add(it.toExternalForm())
			}
		}
		stage.title = "rCrif Layout Tool"
		stage.show()
		Platform.runLater {
			procedureComboBox.requestFocus()
			procedureComboBox.editor.requestFocus()
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
						mapper.readValue(input, ProcedureCallActivityDefinition::class.java)
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
				file.inputStream().use { input -> mapper.readValue(input, ProcedureCallActivityDefinition::class.java) }
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


	private fun getAllConnectors(selectedProcess: String): List<DataSourceActivityDefinition> {
		val mapper = XmlMapper()

		val dataSourceActivityDefinitions = File(selectedProcess, "Procedures")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, DataSourceActivityDefinition::class.java) }
			}
			.filter { activity -> activity.connectorName != null }

		val propertiesFile = File(selectedProcess, "MainFlow")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, DataSourceActivityDefinition::class.java) }
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
		val guard = AtomicBoolean(false)

		fun clampRatio(r: Double) = r.coerceIn(0.0, 1.0)

		listOf(p1 to p2, p2 to p1).forEach { (src, dst) ->
			src.addEventFilter(ScrollEvent.SCROLL) { ev ->
				if (ev.deltaY == 0.0 || !guard.compareAndSet(false, true)) return@addEventFilter
				dst.scrollYBy(-ev.deltaY)
				guard.set(false)
			}
			src.estimatedScrollYProperty().addListener { _, _, newY ->
				if (!guard.compareAndSet(false, true)) return@addListener
				Platform.runLater {
					val totalSrcVal = src.totalHeightEstimateProperty().value
					val totalDstVal = dst.totalHeightEstimateProperty().value
					if (totalSrcVal != null && totalDstVal != null && totalSrcVal > 0 && totalDstVal > 0) {
						val ratio = clampRatio(newY.toDouble() / totalSrcVal)
						dst.scrollYToPixel(ratio * totalDstVal)
					}
					guard.set(false)
				}
			}
		}
	}

	private fun subscribeToChanges(
		area1: CodeArea,
		area2: CodeArea,
		prevBtn: Button,
		nextBtn: Button,
		margeArea1: CodeArea,
		margeArea2: CodeArea
	) {
		val changes1: EventStream<*> = area1.plainTextChanges()
		val changes2: EventStream<*> = area2.plainTextChanges()
		val merged: EventStream<*> = changes1.or(changes2)
		subscription = merged.successionEnds(Duration.ofMillis(300)).subscribe {
			recalculateDiff(area1, area2, prevBtn, nextBtn, margeArea1, margeArea2)
		}
	}


	private fun recalculateDiff(
		area1: CodeArea,
		area2: CodeArea,
		prevBtn: Button,
		nextBtn: Button,
		margeArea1: CodeArea,
		margeArea2: CodeArea
	) {
		val lines1 = area1.text.lines()
		val lines2 = area2.text.lines()
		val (spansL, spansR, nav) = buildSpansAndNav(lines1, lines2)

		area1.setStyleSpans(0, spansL)
		area2.setStyleSpans(0, spansR)

		diffNav.clear()
		diffNav.addAll(nav)
		diffIdx = if (diffNav.isEmpty()) -1 else 0
		val enabled = diffNav.isNotEmpty()
		prevBtn.isDisable = !enabled
		nextBtn.isDisable = !enabled
		if (enabled) {
			gotoDiff(0, margeArea1, margeArea2)
		}

		highlightDiff(area1, lines1, lines2, changeStyle = "diff-delete")
		highlightDiff(area2, lines2, lines1, changeStyle = "diff-insert")
	}


	/**
	 * area  – CodeArea, куда применяем стили
	 * original/other – содержимое по строкам (без переводов)
	 * changeStyle – стиль для изменённого куска в этой области:
	 *               для левой:  "diff-delete", для правой: "diff-insert"
	 */
	private fun highlightDiff(area: CodeArea, original: List<String>, other: List<String>, changeStyle: String) {
		val cleanOrig = original.map { it.replace(uidRegex, """UID=""") }
		val cleanOther = other.map { it.replace(uidRegex, """UID=""") }
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
							val origLine = s
							val newLine = t
							val prefix = origLine.commonPrefixWith(newLine).length
							val maxSuf = min(origLine.length, newLine.length) - prefix
							var suf = 0
							while (suf < maxSuf &&
								origLine[origLine.length - 1 - suf] ==
								newLine[newLine.length - 1 - suf]
							) suf++

							if (prefix > 0) addPlain(prefix)
							val diffLen = origLine.length - prefix - suf
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
	private fun buildSpansAndNav(
		left: List<String>,
		right: List<String>
	): Triple<StyleSpans<Collection<String>>, StyleSpans<Collection<String>>, List<DiffNav>> {
		val cleanL = left.map { it.replace(uidRegex, """UID=""") }
		val cleanR = right.map { it.replace(uidRegex, """UID=""") }
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
		for (d in deltas) {
			while (li < d.source.position) {
				addPlainL(lenL(li)); li++
			}
			while (ri < d.target.position) {
				addPlainR(lenR(ri)); ri++
			}
			when (d.type) {
				DeltaType.DELETE -> {
					d.source.lines.forEachIndexed { j, s ->
						addDelL(lenL(li + j))
						nav += DiffNav(
							lStart = startsL[li + j], lEnd = startsL[li + j] + s.length,
							rStart = null, rEnd = null
						)
					}
					li += d.source.lines.size
				}

				DeltaType.INSERT -> {
					d.target.lines.forEachIndexed { j, s ->
						addInsR(lenR(ri + j))
						nav += DiffNav(
							lStart = null, lEnd = null,
							rStart = startsR[ri + j], rEnd = startsR[ri + j] + s.length
						)
					}
					ri += d.target.lines.size
				}

				DeltaType.CHANGE -> {
					val m = max(d.source.lines.size, d.target.lines.size)
					for (j in 0 until m) {
						val sL = d.source.lines.getOrNull(j)
						val sR = d.target.lines.getOrNull(j)

						if (sL != null && sR != null) {
							val orig = sL
							val neu = sR
							val pref = orig.commonPrefixWith(neu).length
							val maxS = min(orig.length, neu.length) - pref
							var suf = 0
							while (suf < maxS && orig[orig.length - 1 - suf] == neu[neu.length - 1 - suf]) {
								suf++
							}
							if (pref > 0) addPlainL(pref)
							val diffL = orig.length - pref - suf
							if (diffL > 0) addDelL(diffL)
							addPlainL(suf + if (li < left.lastIndex) 1 else 0)
							if (pref > 0) addPlainR(pref)
							val diffR = neu.length - pref - suf
							if (diffR > 0) addInsR(diffR)
							addPlainR(suf + if (ri < right.lastIndex) 1 else 0)
							val lStart = startsL[li] + pref
							val rStart = startsR[ri] + pref
							nav += DiffNav(
								lStart = if (diffL > 0) lStart else null,
								lEnd = if (diffL > 0) lStart + diffL else null,
								rStart = if (diffR > 0) rStart else null,
								rEnd = if (diffR > 0) rStart + diffR else null,
							)
							li++; ri++
						} else if (sL != null) {
							addDelL(lenL(li))
							addPlainR(0)
							nav += DiffNav(
								lStart = startsL[li], lEnd = startsL[li] + sL.length,
								rStart = null, rEnd = null
							)
							li++
						} else {
							addPlainL(0)
							addInsR(lenR(ri))
							nav += DiffNav(
								lStart = null, lEnd = null,
								rStart = startsR[ri], rEnd = startsR[ri] + sR!!.length
							)
							ri++
						}
					}
				}

				DeltaType.EQUAL -> {
					d.source.lines.forEach { _ ->
						addPlainL(lenL(li)); addPlainR(lenR(ri))
						li++; ri++
					}
				}
			}
		}
		while (li < left.size) {
			addPlainL(lenL(li)); li++
		}
		while (ri < right.size) {
			addPlainR(lenR(ri)); ri++
		}
		return Triple(spansL.create(), spansR.create(), nav)
	}


	private fun gotoDiff(step: Int, margeArea1: CodeArea, margeArea2: CodeArea) {
		if (diffNav.isEmpty()) return
		if (step != 0) {
			diffIdx = (diffIdx + step).mod(diffNav.size)
		} else if (diffIdx == -1) {
			diffIdx = 0
		}
		val d = diffNav[diffIdx]
		d.lStart?.let { s ->
			val e = d.lEnd!!
			margeArea1.selectRange(s, e)
			val para = margeArea1.offsetToPosition(s, Bias.Forward).major
			margeArea1.showParagraphAtTop(max(para - 3, 0))
		}
		d.rStart?.let { s ->
			val e = d.rEnd!!
			margeArea2.selectRange(s, e)
			val para = margeArea2.offsetToPosition(s, Bias.Forward).major
			margeArea2.showParagraphAtTop(max(para - 3, 0))
		}
	}


	override fun stop() {
		subscription?.unsubscribe()
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
		private val uidRegex = Regex("""\bUID="[^"]*"""")
	}

}


fun main() {
	Application.launch(RCrifLayoutTool::class.java)
}